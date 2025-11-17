package org.jboss.pnc.gradlemanipulator.manipulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class SimpleNonJavaProjectFunctionalTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Test
    public void ensurePublish() throws IOException, URISyntaxException {
        final File simpleProjectRoot = tempDir.newFolder("simple-non-java-project");
        TestUtils.copyDirectory("simple-non-java-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("--info", "publish")
                .build();

        assertThat(buildResult.task(":" + "publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(publishDirectory).exists();
    }

}
