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
import org.jboss.gm.common.Configuration;
import org.slf4j.Logger;

public class PluginUtils {
    private static final Map<String, String> SUPPORTED_PLUGINS = new HashMap<>();
    private static final List<String> BUILD_FILES = Arrays.asList("build.gradle", "settings.gradle", "build.gradle.kts",
            "settings.gradle.kts");

    static {
        SUPPORTED_PLUGINS.put("com.github.ben-manes.versions", "dependencyUpdates");
        SUPPORTED_PLUGINS.put("com.github.burrunan.s3-build-cache", "buildCache");
        SUPPORTED_PLUGINS.put("gradle-enterprise", "gradleEnterprise");
    }

    /**
     * Removes plugins from a target build file.
     *
     * Will automatically examine build.gradle, settings.gradle, build.gradle.kts, settings.gradle.kts
     *
     * @param logger the current logger in use
     * @param configuration the current configuration
     * @param t the build target directory
     * @throws ManipulationException if an error occurs
     */
    public static void pluginRemoval(Logger logger, Configuration configuration, File t)
            throws ManipulationException {
        final String[] pluginRemoval = configuration.pluginRemoval();

        if (pluginRemoval != null) {
            for (String plugin : pluginRemoval) {
                if (!SUPPORTED_PLUGINS.containsKey(plugin)) {
                    throw new ManipulationException("No support for removing plugin {}", plugin);
                }
                for (String b : BUILD_FILES) {
                    File buildFile = new File(t, b);

                    if (!buildFile.exists()) {
                        logger.debug("No {} found in {}", b, t);
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
                            String target = String.join(eol, lines);
                            // TODO: Should we index to <whitespace>configTask to narrow down the search
                            int startIndex = target.indexOf(configTask);

                            // If there is a configuration block...
                            if (startIndex != -1) {
                                int endIndex = startIndex;
                                int bracketCount = 1;
                                // Find the first opening bracket of the configuration block
                                while (target.charAt(endIndex) != '{') {
                                    endIndex++;
                                }
                                // TODO: Handle comments.
                                // Calculate the end of the configuration block. Start from just after the first bracket
                                for (int i = ++endIndex; i < target.length() && bracketCount != 0; i++, endIndex++) {
                                    char current = target.charAt(i);
                                    if (current == '{') {
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
                                        target.substring(startIndex, endIndex));
                                StringBuilder sb = new StringBuilder(target);
                                // The string split/join can lose the trailing new line so force append it.
                                sb.append(eol);
                                target = sb.delete(startIndex, endIndex).toString();
                            }

                            logger.info("Removed instances of plugin {}", plugin);
                            FileUtils.writeStringToFile(buildFile, target, Charset.defaultCharset());
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
}
