package org.jboss.pnc.gradlemanipulator.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class CarrotSearchHPPCFunctionalTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Test
    public void testHPPC() throws IOException, URISyntaxException, GitAPIException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.7")) == 0);

        final File hppcProjectRoot = tempDir.newFolder("hppc");

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/carrotsearch/hppc.git")
                .setDirectory(hppcProjectRoot)
                .setBranch("0.10.0")
                .setBranchesToClone(Collections.singletonList("refs/tags/0.10.0"))
                .setDepth(1)
                .setNoTags()
                .call()) {
            System.out.println("Cloned hppc to " + hppcProjectRoot);
        }

        TestUtils.copyDirectory("hppc", hppcProjectRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(hppcProjectRoot)
                .withArguments("assemble", "publish")
                .forwardOutput()
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":hppc:publish").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(
                new File(
                        publishDirectory,
                        "com/carrotsearch/hppc/0.10.0.redhat-00002/hppc-0.10.0.redhat-00002.jar"))
                .exists();
        assertThat(systemOutRule.getLinesNormalized()).contains("Found signing plugin; disabling");
    }
}
