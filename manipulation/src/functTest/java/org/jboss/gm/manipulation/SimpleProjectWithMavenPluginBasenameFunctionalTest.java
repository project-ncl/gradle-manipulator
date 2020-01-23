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
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleProjectWithMavenPluginBasenameFunctionalTest {

    private static final String ARTIFACT_NAME = "base-name-1.0.1-redhat-00001";
    private static final Path PATH_IN_REPOSITORY = Paths.get("org/acme/base-name/1.0.1-redhat-00001/");

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ensureProperPomGeneratedForLegacyPlugin() throws IOException, URISyntaxException {
        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File simpleProjectRoot = tempDir.newFolder("simple-project-with-maven-plugin-and-basename");
        TestUtils.copyDirectory("simple-project-with-maven-plugin-and-basename", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                //.withDebug(true)
                .withArguments("uploadArchives", "--stacktrace", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();
        assertThat(buildResult.task(":install").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(buildResult.task(":uploadArchives").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final File repoPathToPom = m2Directory.toPath().resolve(PATH_IN_REPOSITORY).resolve(ARTIFACT_NAME + ".pom").toFile();

        // verify installed artifacts
        verifyArtifacts(m2Directory);
        assertThat(FileUtils.readFileToString(repoPathToPom, Charset.defaultCharset())).contains("Apache License, Version")
                .contains("artifactId>base-name");

        // verify published artifacts
        verifyArtifacts(publishDirectory);
        assertThat(FileUtils.readFileToString(repoPathToPom, Charset.defaultCharset())).contains("Apache License, Version")
                .contains("artifactId>base-name");
    }

    private void verifyArtifacts(File repoDirectory) {
        Path pathToArtifacts = repoDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom").toFile().exists()).isTrue();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar").toFile().exists()).isTrue();
    }
}
