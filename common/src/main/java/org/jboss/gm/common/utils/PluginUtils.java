package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.FileIO;
import org.slf4j.Logger;

public class PluginUtils {
    private static final Map<String, String> SUPPORTED_PLUGINS = new HashMap<>();
    private static final List<String> BUILD_FILES = Arrays.asList("build.gradle", "settings.gradle", "build.gradle.kts",
            "settings.gradle.kts");

    public static final String SEMANTIC_BUILD_VERSIONING = "net.vivin.gradle-semantic-build-versioning";

    static {
        SUPPORTED_PLUGINS.put("com.github.ben-manes.versions", "dependencyUpdates");
        SUPPORTED_PLUGINS.put("com.github.burrunan.s3-build-cache", "buildCache");
        SUPPORTED_PLUGINS.put("gradle-enterprise", "gradleEnterprise");
        SUPPORTED_PLUGINS.put(SEMANTIC_BUILD_VERSIONING, "preRelease");
    }

    /**
     * Removes plugins from a target build file.
     *
     * Will automatically examine build.gradle, settings.gradle, build.gradle.kts, settings.gradle.kts
     *
     * @param logger the current logger in use
     * @param target the build target directory
     * @param plugins the plugins to remove
     * @throws ManipulationException if an error occurs
     */
    public static void pluginRemoval( Logger logger, File target, String[] plugins )
            throws ManipulationException {

        if (plugins != null) {
            for (String plugin : plugins) {
                if (!SUPPORTED_PLUGINS.containsKey(plugin)) {
                    throw new ManipulationException("No support for removing plugin {}", plugin);
                }
                for (String b : BUILD_FILES) {
                    File buildFile = new File(target, b);

                    if (!buildFile.exists()) {
                        logger.debug("No {} found in {}", b, target);
                        continue;
                    }

                    try {
                        List<String> lines = org.apache.commons.io.FileUtils.readLines(buildFile, Charset.defaultCharset());
                        String configTask = SUPPORTED_PLUGINS.get(plugin);
                        String eol;
                        try {
                            eol = FileIO.determineEOL(buildFile).value();
                        } catch (ManipulationException e) {
                            logger.warn("Unable to determine EOL for {}", buildFile);
                            eol = "\n";
                        }

                        logger.debug("Looking to remove {} with configuration block of {}", plugin, configTask);
                        if (lines.removeIf(i -> i.contains(plugin))) {
                            String content = String.join(eol, lines);
                            // TODO: Should we index to <whitespace>configTask to narrow down the search
                            int startIndex = content.indexOf(configTask);

                            // If there is a configuration block...
                            if (startIndex != -1) {
                                int endIndex = startIndex;
                                int bracketCount = 1;
                                boolean inComment = false;
                                // Find the first opening bracket of the configuration block
                                while (content.charAt(endIndex) != '{') {
                                    endIndex++;
                                }
                                // Calculate the end of the configuration block. Start from just after the first bracket
                                for (int i = ++endIndex; i < content.length() && bracketCount != 0; i++, endIndex++) {
                                    char current = content.charAt(i);
                                    if (inComment) {
                                        // Nest this so we always hit in comment blocks until we are ready
                                        // to exit
                                        if (content.charAt(i + 1) == eol.charAt(0)) {
                                            inComment = false;
                                        }
                                    } else if (current == '/' && content.charAt(i + 1) == '/') {
                                        inComment = true;
                                    } else if (current == '{') {
                                        bracketCount++;
                                    } else if (current == '}') {
                                        bracketCount--;
                                    }
                                }
                                if (bracketCount != 0) {
                                    throw new ManipulationException(
                                            "Unable to locate configuration block {} to remove within {}",
                                            configTask, buildFile);
                                }
                                logger.debug("Removing plugin configuration block of {}",
                                        content.substring(startIndex, endIndex));
                                StringBuilder sb = new StringBuilder(content);
                                // The string split/join can lose the trailing new line so force append it.
                                sb.append(eol);
                                content = sb.delete(startIndex, endIndex).toString();
                            }

                            logger.info("Removed instances of plugin {}", plugin);
                            FileUtils.writeStringToFile(buildFile, content, Charset.defaultCharset());
                        } else {
                            logger.trace("Unable to find {} in {}", plugin, buildFile);
                        }
                    } catch (IOException e) {
                        throw new ManipulationException("Unable to read build file {}", buildFile, e);
                    }
                }
            }
        }
    }

    public static boolean checkForSemanticBuildVersioning(Logger logger, File target)
            throws ManipulationException {
        File settingsFile = new File(target, "settings.gradle");
        if (settingsFile.exists()) {
            try {
                List<String> lines = FileUtils.readLines(settingsFile, Charset.defaultCharset());
                for (String s : lines) {
                    if (s.contains(SEMANTIC_BUILD_VERSIONING)) {
                        logger.info("Found Semantic Build Versioning Plugin: {}", s);
                        return true;
                    }
                }
            } catch (IOException e) {
                throw new ManipulationException("Unable to read build file {}", settingsFile, e);
            }
        }
        return false;
    }
}
