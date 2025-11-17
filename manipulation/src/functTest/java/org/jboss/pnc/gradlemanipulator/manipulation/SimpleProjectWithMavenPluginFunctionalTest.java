package org.jboss.pnc.gradlemanipulator.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.common.io.ManipulationIO;
import org.jboss.pnc.gradlemanipulator.common.model.ManipulationModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class SimpleProjectWithMavenPluginFunctionalTest {

    private static final String ARTIFACT_NAME = "root-1.0.1-redhat-00001";
    private static final Path PATH_IN_REPOSITORY = Paths.get("org/acme/root/1.0.1-redhat-00001/");

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Test
    public void ensureProperPomGeneratedForLegacyPlugin() throws IOException, URISyntaxException,
            XmlPullParserException {
        // XXX: Caused by: org.gradle.api.plugins.UnknownPluginException: Plugin [id: 'maven'] was not found in any of
        // XXX: the following sources
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

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
                .withArguments("uploadArchives", "--stacktrace", "--info")
                .build();
        assertThat(buildResult.task(":install").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(buildResult.task(":uploadArchives").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final String repoPathToPom = PATH_IN_REPOSITORY.resolve(ARTIFACT_NAME + ".pom").toString();

        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains(
                                "Replacing strictly with forced version for ch.qos.logback:logback-classic:1.1.3 with ch.qos.logback:logback-classic:1.1.2"));

        // verify installed artifacts
        verifyArtifacts(m2Directory);
        verifyPom(m2Directory, repoPathToPom, alignment);

        // verify published artifacts
        verifyArtifacts(publishDirectory);
        verifyPom(publishDirectory, repoPathToPom, alignment);
    }

    private void verifyPom(File repoDirectory, String pathToPom, ManipulationModel alignment)
            throws IOException, XmlPullParserException {
        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(
                repoDirectory,
                alignment,
                pathToPom,
                true);
        final ManipulationModel module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "hibernate-core"),
                        TestUtils.getAlignedTuple(module, "undertow-core"),
                        TestUtils.getAlignedTuple(module, "junit", "4.12"),
                        TestUtils.getAlignedTuple(module, "logback-classic", "1.1.2"));
        assertThat(modelAndModule.getLeft().getOrganization().getName()).isEqualTo("JBoss");
        assertThat(modelAndModule.getLeft().getLicenses().get(0).getName()).isEqualTo("Apache License, Version 2.0");
    }

    private void verifyArtifacts(File repoDirectory) {
        Path pathToArtifacts = repoDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom")).exists();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar")).exists();
        assertThat(pathToArtifacts.resolve("ivy-1.0.1-redhat-00001.xml")).doesNotExist();
    }
}
