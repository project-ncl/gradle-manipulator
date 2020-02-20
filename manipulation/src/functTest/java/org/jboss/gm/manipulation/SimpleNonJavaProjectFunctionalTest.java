package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleNonJavaProjectFunctionalTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

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
                .forwardOutput()
                .withPluginClasspath()
                //.withDebug(true)
                .build();

        assertThat(buildResult.task(":" + "publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(publishDirectory).exists();
    }

}
