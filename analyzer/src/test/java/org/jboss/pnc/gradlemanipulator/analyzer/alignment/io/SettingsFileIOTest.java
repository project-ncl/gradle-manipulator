package org.jboss.pnc.gradlemanipulator.analyzer.alignment.io;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.impldep.com.google.api.client.googleapis.testing.TestUtils;
import org.jboss.pnc.gradlemanipulator.common.rules.LoggingRule;
import org.jboss.pnc.gradlemanipulator.common.utils.PluginUtils;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class SettingsFileIOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final SystemPropertiesRule restoreSystemProperties = new SystemPropertiesRule();

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
        File testPath = new File(TestUtils.class.getClassLoader().getResource("").getPath()).getParentFile()
                .getParentFile()
                .getParentFile();
        File testSettings = new File(testPath, "tmp" + File.separator + "tests");
        testSettings.mkdirs();
        File settingsGradle = new File(testSettings, "settings.gradle");
        settingsGradle.createNewFile();
        String result = SettingsFileIO.writeProjectNameIfNeeded(testSettings);
        assertEquals("gradle-manipulator", result);
        // Clean up so we can rerun and still succeed.
        FileUtils.deleteQuietly(testSettings);
    }

    @Test(expected = ManipulationUncheckedException.class)
    public void testFindGitDir() throws IOException {
        File tmpFolder = folder.newFolder();
        File subdir = new File(tmpFolder, "subfolder");

        SettingsFileIO.findGitDir(subdir);
    }

    @Test
    public void testSettingsDokka() throws IOException {
        File tmpFolder = folder.newFolder();
        File settingsGradle = new File(tmpFolder, "settings.gradle");
        FileUtils.writeStringToFile(settingsGradle, "rootProject.name = 'reactor'", Charset.defaultCharset());
        SettingsFileIO.writeDokkaSettings(tmpFolder, PluginUtils.DokkaVersion.MINIMUM);
        String result = FileUtils.readFileToString(settingsGradle, Charset.defaultCharset());
        assertEquals(StringUtils.countMatches(result, "{"), StringUtils.countMatches(result, "}"));
        assertTrue(result.contains("pluginManagement { resolutionStrategy { eachPlugin { if (requested.id.id "));
    }

    @Test
    public void testSettingsNoDokka() throws IOException {
        File tmpFolder = folder.newFolder();
        File settingsGradle = new File(tmpFolder, "settings.gradle");
        FileUtils.writeStringToFile(settingsGradle, "rootProject.name = 'reactor'", Charset.defaultCharset());
        SettingsFileIO.writeDokkaSettings(tmpFolder, PluginUtils.DokkaVersion.NONE);
        String result = FileUtils.readFileToString(settingsGradle, Charset.defaultCharset());
        assertEquals(StringUtils.countMatches(result, "{"), StringUtils.countMatches(result, "}"));
        assertFalse(result.contains("pluginManagement { resolutionStrategy { eachPlugin { if (requested.id.id "));
    }

    @Test
    public void testSettingsDokkaExisting() throws IOException {
        File tmpFolder = folder.newFolder();
        File settingsGradle = new File(tmpFolder, "settings.gradle");
        FileUtils.writeStringToFile(
                settingsGradle,
                "pluginManagement\n\n{\n\n}\nrootProject.name = 'reactor'",
                Charset.defaultCharset());
        SettingsFileIO.writeDokkaSettings(tmpFolder, PluginUtils.DokkaVersion.MINIMUM);
        String result = FileUtils.readFileToString(settingsGradle, Charset.defaultCharset());
        assertTrue(
                result.contains(
                        "pluginManagement {\nresolutionStrategy {\n"
                                + " eachPlugin { if (requested.id.id == \"org.jetbrains.dokka\") { useVersion"
                                + "(\"0.9.18\") } }"));
        assertEquals(StringUtils.countMatches(result, "{"), StringUtils.countMatches(result, "}"));
    }

    @Test
    public void testSettingsDokkaExisting2() throws IOException {
        File tmpFolder = folder.newFolder();
        File settingsGradle = new File(tmpFolder, "settings.gradle");
        FileUtils.writeStringToFile(
                settingsGradle,
                "/*\n*\n*/\npluginManagement\n\n{\nresolutionStrategy "
                        + "{}\n}\nrootProject.name = "
                        + "'reactor'",
                Charset.defaultCharset());
        SettingsFileIO.writeDokkaSettings(tmpFolder, PluginUtils.DokkaVersion.MINIMUM);
        String result = FileUtils.readFileToString(settingsGradle, Charset.defaultCharset());
        assertEquals(StringUtils.countMatches(result, "{"), StringUtils.countMatches(result, "}"));
        assertTrue(
                result.contains(
                        "resolutionStrategy {\n"
                                + " eachPlugin { if (requested.id.id == \"org.jetbrains.dokka\") { useVersion"
                                + "(\"0.9.18\") } }"));
    }
}
