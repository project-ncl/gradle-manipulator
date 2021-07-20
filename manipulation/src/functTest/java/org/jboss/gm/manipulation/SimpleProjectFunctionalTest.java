package org.jboss.gm.manipulation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.internal.Pair;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.gradle.util.GradleVersion;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class SimpleProjectFunctionalTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ignoreMissingManipulationFile() throws IOException, URISyntaxException {
        // NCL-6050: Could not get unknown property 'generatePomFileForPluginMavenPublication' for project
        // ':build-tools' of type org.gradle.api.Project.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();
        Files.delete(Paths.get(simpleProjectRoot.toString(), ManipulationIO.MANIPULATION_FILE_NAME));

        TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("generatePomFileForMainPublication")
                //.withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();

        assertTrue(systemOutRule.getLog().contains("No manipulation.json found in"));
    }

    @Test
    public void testDisableGME() throws IOException, URISyntaxException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("-Dmanipulation.disable=true", "generatePomFileForMainPublication")
                //.withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();

        assertTrue(systemOutRule.getLog().contains("Gradle Manipulator disabled"));
    }

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException, XmlPullParserException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final ManipulationModel alignment = ManipulationIO.readManipulationModel(simpleProjectRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("--info", "generatePomFileForMainPublication")
                //.withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + "generatePomFileForMainPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(simpleProjectRoot, alignment,
                "build/publications/main/pom-default.xml");

        final ManipulationModel module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "hibernate-core"),
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "undertow-core"));
    }

    @Test
    public void ensureDocs() throws IOException, URISyntaxException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("distZip")
                //.withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + "distZip").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(simpleProjectRoot.toPath().resolve("build/distributions/dummy-1.0.1-redhat-00001-docs.zip")).exists();
        assertThat(simpleProjectRoot.toPath().resolve("build/distributions/dummy-1.0.1-redhat-00001-dist.zip")).exists();
    }

    @Test
    public void ensurePublish() throws IOException, URISyntaxException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("--info", "publish")
                //.withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();

        final String ARTIFACT_VERSION = "1.0.1-redhat-00001";
        final Path PATH_IN_REPOSITORY = Paths.get("org/acme/root/" + ARTIFACT_VERSION);
        final File repoPathToJar = publishDirectory.toPath().resolve(PATH_IN_REPOSITORY)
                .resolve("root-" + ARTIFACT_VERSION + ".jar")
                .toFile();

        try (JarInputStream jarStream = new JarInputStream(new FileInputStream(repoPathToJar))) {
            final Manifest manifest = jarStream.getManifest();
            assertThat(manifest.getMainAttributes().getValue("Implementation-Version")).contains(ARTIFACT_VERSION);
        }

        assertThat(buildResult.task(":" + "publishMainPublicationToJboss-snapshots-repositoryRepository").getOutcome())
                .isEqualTo(TaskOutcome.SKIPPED);
        assertThat(buildResult.task(":" + "publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(publishDirectory).exists();
        assertTrue(systemOutRule.getLog().contains("as buildSrc is build-time only and not configured for publishing"));
    }

    @Test
    public void enforceDeployPluginThatWasAlreadyConfigured() throws Exception {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("--info", "publish", "-DdeployPlugin=maven-publish")
                .forwardOutput()
                .withPluginClasspath()
                //.withDebug(true)
                .build();

        assertThat(buildResult.task(":" + "publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test(expected = UnexpectedBuildFailure.class)
    public void enforceDifferentDeployPluginThanConfigured() throws Exception {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("--info", "publish", "-DdeployPlugin=maven")
                .forwardOutput()
                .withPluginClasspath()
                //.withDebug(true)
                .build();
    }
}
