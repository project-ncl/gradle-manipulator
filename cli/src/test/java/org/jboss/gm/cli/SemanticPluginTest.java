package org.jboss.gm.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.linesOf;

import java.io.File;
import java.util.Collections;
import org.aeonbits.owner.ConfigCache;
import org.eclipse.jgit.api.Git;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.jboss.gm.common.utils.PluginUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

public class SemanticPluginTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

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
