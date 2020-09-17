package org.jboss.gm.analyzer.alignment.io;

import java.io.File;
import java.io.IOException;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.impldep.com.google.api.client.googleapis.testing.TestUtils;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;

public class SettingsFileIOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

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
        File tmpFolder = folder.newFolder();
        File subdir = new File(tmpFolder, "subfolder");
        File root = new File(TestUtils.class.getClassLoader().getResource("").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile();
        // Weird hack to work around debugging in IntelliJ versus running in Gradle project build.
        if ( root.getName().equals( "analyzer" )) {
            root = root.getParentFile();
        }

        System.setProperty(Configuration.CLI_WORKING_DIR, root.getCanonicalPath());

        subdir.mkdirs();
        File settingsGradle = new File(subdir, "settings.gradle");
        settingsGradle.createNewFile();
        SettingsFileIO.writeProjectNameIfNeeded(subdir);
    }
}
