package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class KafkaProjectWithMavenPluginFunctionalTest {

    private static final String ARTIFACT_NAME = "connect-runtime-2.6.0.redhat-00001";
    private static final Path PATH_IN_REPOSITORY = Paths.get("org/apache/kafka/connect-runtime/2.6.0.redhat-00001/");

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void verifyProjectNamingOverrides() throws IOException, URISyntaxException {
        // XXX: 'build.gradle': 203: unable to resolve class MavenDeployment
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File kafka = tempDir.newFolder("kafka2");
        TestUtils.copyDirectory("kafka2", kafka);
        assertThat(kafka.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(kafka)
                .withArguments("uploadArchives", "--info")
                .build();
        assertThat(buildResult.task(":uploadArchives").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        Path pathToArtifacts = publishDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        File repoPathToPom = pathToArtifacts.resolve(ARTIFACT_NAME + ".pom").toFile();
        assertTrue(systemOutRule.getLog().contains(
                "Located archivesBaseName override ; forcing project name to 'connect-runtime' from 'runtime' for correct usage"));
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom")).exists();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar")).exists();
        assertThat(FileUtils.readFileToString(repoPathToPom, Charset.defaultCharset()))
                .contains("artifactId>connect-transforms");
    }

    @Test
    public void verifyForceOverride() throws IOException, URISyntaxException {
        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File kafka = tempDir.newFolder("kafka3");
        TestUtils.copyDirectory("kafka3", kafka);
        assertThat(kafka.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(kafka)
                .withGradleVersion("7.2")
                .withArguments("--info", "-PskipSigning=true", "-PscalaVersion=2.12", "-PscalaOptimizerMode=inline"
                        + "-scala",
                        "assemble", "releaseRedHatZip", "uploadArchives", "-x", "test")
                .build();
        assertThat(buildResult.task(":assemble").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        File pathToZip = new File(kafka, "core/build/distributions/kafka_2.13-3.0.0.redhat-00009.zip");
        assertThat(pathToZip.toPath()).isRegularFile().isReadable();

        try (final ZipFile zipFile = new ZipFile(pathToZip)) {
            assertThat(zipFile.stream().filter(
                    f -> f.getName().endsWith("jar") && !f.getName().replaceAll(
                            "^.*/",
                            "").contains(
                                    "redhat"))
                    .count()).isEqualTo(0);
        }
        assertTrue(systemOutRule.getLog().contains("Replacing force override of org.javassist:javassist:3.27.0-GA "
                + "with org.javassist:javassist:3.27.0.GA-redhat-00001"));
    }
}
