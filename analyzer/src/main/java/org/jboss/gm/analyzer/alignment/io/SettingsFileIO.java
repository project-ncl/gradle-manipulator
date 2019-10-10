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

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;

import static org.apache.commons.lang.StringUtils.isEmpty;

public final class SettingsFileIO {
    private static final Pattern SETTINGS_ROOT_PROJECT = Pattern.compile(".*rootProject.name\\s*=\\s*['\"](.*)['\"]");
    private static final Pattern SCM_URL_LINE_EXPR = Pattern.compile("\\s*url\\s*=(?:.*/)([a-zA-Z0-9\\-._]+)");

    private static Logger logger = GMLogger.getLogger(SettingsFileIO.class);

    private SettingsFileIO() {
    }

    // we need to make sure that the name of the root project is stored if not set
    // this is because the manipulation plugin must use the same name
    // otherwise the model won't be found
    // see also: https://discuss.gradle.org/t/rootproject-name-in-settings-gradle-vs-projectname-in-build-gradle/5704/4
    public static String writeProjectNameIfNeeded(File rootDir) throws IOException {
        String result = null;

        for (String s : Arrays.asList("settings.gradle", "settings.gradle.kts")) {
            if (result != null) {
                break;
            }
            File settingsGradle = new File(rootDir, s);
            // Might not exist for a single project build.
            if (settingsGradle.exists()) {
                List<String> lines = FileUtils.readLines(settingsGradle, Charset.defaultCharset());
                logger.debug("Examining settings.gradle content {} ", lines);
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

    private static String extractProjectNameFromScmUrl(File rootDir) throws IOException {
        File gitConfig = new File(new File(rootDir, ".git"), "config");
        if (!gitConfig.isFile()) {
            throw new ManipulationUncheckedException("No .git/config file found, failed to determine the root project name");
        }
        try {
            List<String> lines = FileUtils.readLines(gitConfig, Charset.defaultCharset());

            logger.debug("Examining git config content {} ", lines);
            for (String line : lines) {
                Matcher matcher = SCM_URL_LINE_EXPR.matcher(line);

                if (matcher.find()) {
                    return matcher.group(1).replaceAll("\\.git$", "");
                }
            }
            // Scanned the entire file and failed to find a match.
            throw new ManipulationUncheckedException(
                    ".git/config file doesn't define SCM URL, failed to determine the root project name. File contents: "
                            + lines);
        } catch (IOException e) {
            throw new IOException("Unable to read .git/config file found, failed to determine the root project name", e);
        }
    }
}
