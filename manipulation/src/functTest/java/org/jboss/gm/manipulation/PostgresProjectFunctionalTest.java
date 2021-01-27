package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class PostgresProjectFunctionalTest {
    private static final String TEST = "pgjdbc";
    private static final String ARTIFACT_NAME = "postgresql-jre7-42.2.18.redhat-00001";
    private static final Path PATH_IN_REPOSITORY = Paths
            .get("org/postgresql/postgresql-jre7/42.2.18.redhat-00001");

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();//.muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ensurePublishAvoidanceAndPublish() throws IOException, URISyntaxException {
        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder(TEST);
        TestUtils.copyDirectory(TEST, projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle.kts")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withDebug(true)
                .withArguments("publish")
                .withPluginClasspath()
                .forwardOutput()
                .build();
        assertThat(buildResult.task(":postgresql:publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        Path pathToArtifacts = publishDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom").toFile().exists()).isTrue();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar").toFile().exists()).isTrue();
        assertTrue(systemOutRule.getLog().contains(
                "Updating publication artifactId (postgresql) as it is not consistent with archivesBaseName (postgresql-jre7)"));
    }
}
