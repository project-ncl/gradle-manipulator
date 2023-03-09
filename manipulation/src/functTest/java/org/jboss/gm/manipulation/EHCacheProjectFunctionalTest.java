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
import static org.jboss.gm.common.JVMTestSetup.JDK8_DIR;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class EHCacheProjectFunctionalTest {

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
    public void ensurePublish()
            throws IOException, URISyntaxException, GitAPIException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.1")) >= 0);
        // XXX: JavaConvention.java fails to compile due to API changes
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0);

        final File simpleProjectRoot = tempDir.newFolder("ehcache3");

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/ehcache/ehcache3.git")
                .setDirectory(simpleProjectRoot)
                .setBranch("refs/tags/v3.10.2")
                .setDepth(1)
                .call()) {
            System.out.println("Cloned ehcache3 to " + simpleProjectRoot);
        }

        // Now copy in overrides to enable this subset of unit test to work:
        //   Modify Maven repository
        //   Modify docs generation
        //   Add Manipulation plugin
        TestUtils.copyDirectory("ehcache3", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final File publishDirectory = tempDir.newFolder("publish");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments(
                        "-Dorg.gradle.java.home=" + JDK8_DIR,
                        "--info", "publish", "-x", "test")
                //.withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();

        assertThat(
                buildResult.task(":clustered:server:ehcache-service:publishMavenJavaPublicationToGMERepository").getOutcome())
                        .isEqualTo(TaskOutcome.SUCCESS);
        assertThat(publishDirectory).exists();
        assertTrue(systemOutRule.getLog()
                .contains("Detected use of conflict resolution strategy strict"));
        assertTrue(systemOutRule.getLog().contains("Signing was detected as enabled - disabling"));
    }
}
