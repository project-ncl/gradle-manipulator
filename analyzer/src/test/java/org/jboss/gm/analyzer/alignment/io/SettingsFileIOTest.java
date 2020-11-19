package org.jboss.gm.analyzer.alignment.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.impldep.com.google.api.client.googleapis.testing.TestUtils;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

public class SettingsFileIOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Test
    public void testSCMName() throws IOException {
        File tmpFolder = folder.newFolder();
        SettingsFileIO.writeProjectNameIfNeeded(tmpFolder);
    }

    @Test(expected = ManipulationUncheckedException.class)
    public void testSCMNameWithNoSCM() throws IOException {
        File tmpFolder = folder.newFolder();
        File settingsGradle = new File(tmpFolder, "settings.gradle");
        settingsGradle.createNewFile();
        SettingsFileIO.writeProjectNameIfNeeded(tmpFolder);
    }

    @Test
    public void testSCMNameWithSCMHigher() throws IOException {
        File testPath = new File(TestUtils.class.getClassLoader().getResource("").getPath()).getParentFile().getParentFile()
                .getParentFile();
        File testSettings = new File(testPath, "tmp" + File.separator + "tests");
        testSettings.mkdirs();
        File settingsGradle = new File(testSettings, "settings.gradle");
        settingsGradle.createNewFile();
        SettingsFileIO.writeProjectNameIfNeeded(testSettings);
        // Clean up so we can rerun and still succeed.
        FileUtils.deleteQuietly(testSettings);
    }

    @Test(expected = ManipulationUncheckedException.class)
    public void testFindGitDir() throws IOException {
        File tmpFolder = folder.newFolder();
        File subdir = new File(tmpFolder, "subfolder");

        SettingsFileIO.findGitDir(subdir);
    }
}
