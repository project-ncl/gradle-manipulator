package org.jboss.gm.analyzer.alignment.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.utils.PluginUtils;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Utility class for settings file I/O.
 */
@UtilityClass
public class SettingsFileIO {
    private final Pattern SETTINGS_ROOT_PROJECT = Pattern.compile(".*rootProject.name\\s*=\\s*['\"](.*)['\"]");
    private final Pattern SCM_URL_LINE_EXPR = Pattern.compile("\\s*url\\s*=(?:.*/)([a-zA-Z0-9\\-._]+)");
    private final String GIT_CONFIG = ".git" + File.separator + "config";

    private final Logger logger = GMLogger.getLogger(SettingsFileIO.class);

    // we need to make sure that the name of the root project is stored if not set
    // this is because the manipulation plugin must use the same name
    // otherwise the model won't be found
    // see also: https://discuss.gradle.org/t/rootproject-name-in-settings-gradle-vs-projectname-in-build-gradle/5704/4

    /**
     * Writes the project name if needed.
     *
     * @param rootDir the root directory of the settings file
     * @return the new project name
     * @throws IOException if an error occurs while writing the settings file
     */
    public String writeProjectNameIfNeeded(File rootDir) throws IOException {
        String result = null;

        for (String s : Arrays.asList("settings.gradle", "settings.gradle.kts")) {
            if (result != null) {
                break;
            }
            File settingsGradle = new File(rootDir, s);
            // Might not exist for a single project build.
            if (settingsGradle.exists()) {
                List<String> lines = FileUtils.readLines(settingsGradle, Charset.defaultCharset());
                logger.debug("Examining settings.gradle content {}", lines);
                for (String line : lines) {
                    Matcher m = SETTINGS_ROOT_PROJECT.matcher(line);
                    if (m.find()) {
                        result = m.group(1);
                        break;
                    }
                }
                if (isEmpty(result)) {
                    result = extractProjectNameFromScmUrl(rootDir);

                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsGradle, true))) {
                        // Ensure the marker is on a line by itself.
                        writer.newLine();
                        writer.write("rootProject.name='" + result + "'");
                        writer.newLine();
                        writer.flush();
                    }
                }
            }
        }
        logger.debug("Returning new project name (artifactId) of {}", result);
        return result;
    }

    /**
     * If the DokkaVersion is less than or equal to 0.9.18 then this should
     * inject the appropriate resolutionStrategy into the settings.gradle
     *
     * @param rootProject the root directory
     * @param dokkaVersion the Dokka plugin version
     * @throws IOException if an error occurs
     */
    public void writeDokkaSettings(File rootProject, PluginUtils.DokkaVersion dokkaVersion)
            throws IOException {
        if (dokkaVersion != PluginUtils.DokkaVersion.MINIMUM) {
            return;
        }
        for (String s : Arrays.asList("settings.gradle", "settings.gradle.kts")) {
            File settings = new File(rootProject, s);
            if (settings.exists()) {
                String settingsContents = FileUtils.readFileToString(settings, Charset.defaultCharset());
                logger.info("Updating {} with Dokka resolutionStrategy information", settings);

                if (settingsContents.contains("resolutionStrategy")) {
                    // Existing strategy so inject another
                    settingsContents = settingsContents.replaceFirst("(?s)resolutionStrategy(\\s|$)+\\{",
                            "resolutionStrategy {\n eachPlugin { if (requested.id.id == \"org.jetbrains.dokka\") { useVersion(\"0.9.18\") } }\n");
                } else if (settingsContents.contains("pluginManagement")) {
                    // Existing pluginManagement but no strategy so inject that
                    settingsContents = settingsContents.replaceFirst("(?s)pluginManagement(\\s|$)+\\{",
                            "pluginManagement {\nresolutionStrategy {\n eachPlugin { if (requested.id.id == \"org.jetbrains.dokka\") { useVersion(\"0.9.18\") } }\n");
                } else {
                    // No pluginManagement so inject everything.
                    settingsContents = "pluginManagement { resolutionStrategy { eachPlugin { if (requested.id.id == \"org.jetbrains.dokka\") { useVersion(\"0.9.18\") } } } }\n"
                            + settingsContents;
                }
                FileUtils.writeStringToFile(settings, settingsContents, Charset.defaultCharset());
            }
        }
    }

    private String extractProjectNameFromScmUrl(File rootDir) throws IOException {
        File gitConfig = findGitDir(rootDir);
        try {
            List<String> lines = FileUtils.readLines(gitConfig, Charset.defaultCharset());

            logger.debug("Examining git config content {}", lines);
            for (String line : lines) {
                Matcher matcher = SCM_URL_LINE_EXPR.matcher(line);

                if (matcher.find()) {
                    return matcher.group(1).replaceAll("\\.git$", "");
                }
            }
            // Scanned the entire file and failed to find a match.
            throw new ManipulationUncheckedException(
                    ".git/config file doesn't define SCM URL, failed to determine the root project name. File contents: {}",
                    lines);
        } catch (IOException e) {
            throw new IOException("Unable to read .git/config file found, failed to determine the root project name", e);
        }
    }

    // Package private for testing
    File findGitDir(File current) {
        File prospective = new File(current, GIT_CONFIG);

        logger.info("Searching for Git config in {}", prospective);
        if (prospective.isFile()) {
            return prospective;
        }
        File parent = current.getParentFile();
        if (parent == null) {
            throw new ManipulationUncheckedException(
                    "No .git/config file found, failed to determine the root project name");
        }
        return findGitDir(parent);
    }
}
