package org.jboss.pnc.gradlemanipulator.manipulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class WarFunctionalTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final String PROJECT_NAME = "war-project";

    private static final String ARTIFACT_ID = "jboss-servlet-api_2.5_spec";

    private static final String VERSION_NEW = "1.0.1.Final";

    @Test
    public void verifyWar() throws IOException, URISyntaxException {

        final File projectRoot = tempDir.newFolder("war-project");
        TestUtils.copyDirectory("war-project", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("--info", "assemble", "publish")
                .forwardOutput()
                .withPluginClasspath()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":war")).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final Path warPath = Paths.get("build", "libs", PROJECT_NAME + ".war");
        final Path warFile = projectRoot.toPath().resolve(warPath);

        assertThat(warFile).isRegularFile().isReadable();

        try (final ZipFile zipFile = new ZipFile(warFile.toFile())) {
            assertThat(
                    zipFile.stream()
                            .filter(zipEntry -> !zipEntry.isDirectory() && zipEntry.getName().endsWith(".jar"))
                            .map(ZipEntry::getName))
                    .containsExactly("WEB-INF/lib/" + ARTIFACT_ID + "-" + VERSION_NEW + ".jar");
        }
    }
}
