package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.jboss.gm.common.JVMTestSetup;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.JVMTestSetup.JDK17_DIR;
import static org.junit.Assume.assumeTrue;

public class OpenTelemetryJavaInstrumentationProjectFunctionalTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @BeforeClass
    public static void setupJVM() throws IOException {
        JVMTestSetup.setupJVM();
    }

    @Test
    public void testOpenTelemetryJavaInstrumentation() throws IOException, URISyntaxException, GitAPIException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.8")) >= 0);

        final File opentelemetryProjectRoot = tempDir.newFolder("opentelemetry-java-instrumentation-project");

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/open-telemetry/opentelemetry-java-instrumentation.git")
                .setDirectory(opentelemetryProjectRoot)
                .setBranch("refs/tags/v2.5.0")
                .setDepth(1)
                .call()) {
            System.out.println("Cloned opentelemetry-java-instrumentation to " + opentelemetryProjectRoot);
        }

        TestUtils.copyDirectory("opentelemetry-java-instrumentation", opentelemetryProjectRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(opentelemetryProjectRoot)
                .withArguments("-q", "-Potel.stable=true", "-Dorg.gradle.java.home=" + JDK17_DIR, "publish", "-x", "test", "-x",
                        "spotlessCheck", "-x", "checkstyleMain", "-x", "javadoc")
                .forwardOutput()
                .withDebug(false)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":bom:publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(new File(publishDirectory,
                "io/opentelemetry/javaagent/opentelemetry-javaagent/2.5.0.redhat-00001/opentelemetry-javaagent-2.5.0.redhat-00001.jar"))
                        .exists();
    }
}
