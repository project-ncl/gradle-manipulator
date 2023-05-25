package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class ElasticSearchProjectFunctionalTest {
    private static final String TEST = "elasticsearch";
    private static final String ARTIFACT_NAME = "transport-netty4-client-6.8.6.temporary-redhat-00001";
    private static final Path PATH_IN_REPOSITORY = Paths
            .get("org/elasticsearch/plugin/transport-netty4-client/6.8.6.temporary-redhat-00001/");

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ensurePublishWithNestedPlugin() throws IOException, URISyntaxException {
        // XXX: Gradle 5.4.1 is required to use elasticsearch.build plugin
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.4.1")) >= 0);

        // XXX: Could not find method minimumRuntimeCompile() for arguments [junit:junit:4.12] on object of type
        // XXX: org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder(TEST);
        TestUtils.copyDirectory(TEST, projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("publish")
                .build();
        assertThat(buildResult.task(":modules:transport-netty4:publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        Path pathToArtifacts = publishDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom")).exists();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar")).exists();
        assertThat(
                FileUtils.readFileToString(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom").toFile(),
                        Charset.defaultCharset())).contains("transport-netty4-client");
        assertTrue(systemOutRule.getLog().contains("Detected application of plugin hook"));
        assertTrue(systemOutRule.getLog().contains("Removing publishing repository test"));
        assertTrue(systemOutRule.getLog().contains("Disabling publishing task publishNebulaPublicationToTestRepository"));
        assertTrue(systemOutRule.getLog().contains("publication has been added but the POM file generation disabled"));
    }

    @Test
    public void ensurePublishWithNestedPluginDisabledHook() throws IOException, URISyntaxException {
        // XXX: Gradle 5.4.1 is required to use elasticsearch.build plugin
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.4.1")) >= 0);

        // XXX: Could not find method minimumRuntimeCompile() for arguments [junit:junit:4.12] on object of type
        // XXX: org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder(TEST);
        TestUtils.copyDirectory(TEST, projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                //.withDebug(true)
                .withArguments("publish", "-DpublishPluginHooks=")
                .withPluginClasspath()
                .forwardOutput()
                .build();
        assertThat(buildResult.task(":modules:transport-netty4:publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        // verify published artifacts
        Path pathToArtifacts = publishDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom")).doesNotExist();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar")).doesNotExist();
        assertFalse(systemOutRule.getLog().contains("Detected application of plugin hook"));
    }
}
