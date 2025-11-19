package org.jboss.pnc.gradlemanipulator.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.linesOf;

import java.io.File;
import java.util.Collections;
import org.aeonbits.owner.ConfigCache;
import org.eclipse.jgit.api.Git;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.jboss.pnc.gradlemanipulator.common.utils.PluginUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class SemanticPluginTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final SystemPropertiesRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final EnvironmentVariablesRule environmentVariables = new EnvironmentVariablesRule();

    @Before
    public void reset() {
        // Reset the daemon between tests : https://discuss.gradle.org/t/stopping-gradle-daemon-via-tooling-api/16004/2
        // Under 4.10 the daemon appears to cache Config values which corrupt the tests.
        ConnectorServices.reset();
        // Spurious caching issues so clear the cache for each test
        ConfigCache.clear();
    }

    @Test
    public void testCruiseControl()
            throws Exception {
        final File folder = tempDir.newFolder();

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/linkedin/cruise-control.git")
                .setDirectory(folder)
                .setBranch("2.5.73")
                .setBranchesToClone(Collections.singletonList("refs/tags/2.5.73"))
                .setDepth(1)
                .setNoTags()
                .call()) {
            System.out.println("Cloned CruiseControl to " + folder);
        }

        File props = new File(folder, "gradle.properties");
        File settings = new File(folder, "settings.gradle");

        assertThat(linesOf(props)).anyMatch(item -> !item.contains("version=2.5.73"));
        assertThat(linesOf(settings)).anyMatch(item -> item.contains(PluginUtils.SEMANTIC_BUILD_VERSIONING));

        Main m = new Main();
        String[] args = new String[] { "--no-colour", "-t", folder.getAbsolutePath(), "tasks" };
        m.run(args);

        assertThat(linesOf(props)).anyMatch(item -> item.contains("version=2.5.73"));
        assertThat(linesOf(settings)).anyMatch(item -> !item.contains(PluginUtils.SEMANTIC_BUILD_VERSIONING));
    }
}
