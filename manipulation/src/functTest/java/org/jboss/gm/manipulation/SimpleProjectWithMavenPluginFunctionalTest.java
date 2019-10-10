package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.internal.Pair;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleProjectWithMavenPluginFunctionalTest {

    private static final String ARTIFACT_NAME = "root-1.0.1-redhat-00001";
    private static final Path PATH_IN_REPOSITORY = Paths.get("org/acme/root/1.0.1-redhat-00001/");

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ensureProperPomGeneratedForLegacyPlugin() throws IOException, URISyntaxException, XmlPullParserException {
        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File simpleProjectRoot = tempDir.newFolder("simple-project-with-maven-plugin");
        TestUtils.copyDirectory("simple-project-with-maven-plugin", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final ManipulationModel alignment = ManipulationIO.readManipulationModel(simpleProjectRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                //.withDebug(true)
                .withArguments("uploadArchives", "--stacktrace", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .forwardOutput()
                .build();
        assertThat(buildResult.task(":install").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(buildResult.task(":uploadArchives").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final String repoPathToPom = PATH_IN_REPOSITORY.resolve(ARTIFACT_NAME + ".pom").toString();

        // verify installed artifacts
        verifyArtifacts(m2Directory);
        verifyPom(m2Directory, repoPathToPom, alignment);

        // verify published artifacts
        verifyArtifacts(publishDirectory);
        verifyPom(publishDirectory, repoPathToPom, alignment);
    }

    private void verifyPom(File repoDirectory, String pathToPom, ManipulationModel alignment)
            throws IOException, XmlPullParserException {
        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(repoDirectory, alignment,
                pathToPom, true);
        final ManipulationModel module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "hibernate-core"),
                        TestUtils.getAlignedTuple(module, "undertow-core"),
                        TestUtils.getAlignedTuple(module, "junit", "4.12"));
        assertThat(modelAndModule.getLeft().getOrganization().getName()).isEqualTo("JBoss");
        assertThat(modelAndModule.getLeft().getLicenses().get(0).getName()).isEqualTo("Apache License, Version 2.0");
    }

    private void verifyArtifacts(File repoDirectory) {
        Path pathToArtifacts = repoDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom").toFile().exists()).isTrue();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar").toFile().exists()).isTrue();
    }
}
