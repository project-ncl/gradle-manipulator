package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.FileIO;
import org.slf4j.Logger;

public class PluginUtils {
    private static final Map<String, String> SUPPORTED_PLUGINS = new HashMap<>();

    public static final String SEMANTIC_BUILD_VERSIONING = "net.vivin.gradle-semantic-build-versioning";

    static {
        SUPPORTED_PLUGINS.put("com.github.ben-manes.versions", "dependencyUpdates");
        SUPPORTED_PLUGINS.put("com.github.burrunan.s3-build-cache", "buildCache");
        SUPPORTED_PLUGINS.put("de.marcphilipp.nexus-publish", "nexusPublishing");
        SUPPORTED_PLUGINS.put("gradle-enterprise", "gradleEnterprise");
        SUPPORTED_PLUGINS.put("io.codearte.nexus-staging", "nexusStaging");
        SUPPORTED_PLUGINS.put("io.github.gradle-nexus.publish-plugin", "nexusPublishing");
        SUPPORTED_PLUGINS.put("signing", "signing");
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
    public static void pluginRemoval(Logger logger, File target, Set<String> plugins)
            throws ManipulationException {

        if (plugins == null || plugins.size() == 0) {
            return;
        } else if (plugins.contains("ALL")) {
            // Shortcut to represent removing any/all of the supported plugins
            plugins.clear();
            plugins.addAll(SUPPORTED_PLUGINS.keySet());
        }

        // As the configuration block is named the same as the plugin we differentiate via the
        // potential quoting mechanism to avoid problems when parsing the file to remove the plugins
        if (plugins.remove("signing")) {
            plugins.add("'signing'");
            plugins.add("\"signing\"");
        }

        Collection<File> files = new HashSet<>();
        final Collection<File> gradleFiles = FileUtils.listFiles(target, new WildcardFileFilter("*.gradle"),
                TrueFileFilter.INSTANCE);
        final Collection<File> kotlinFiles = FileUtils.listFiles(target, new WildcardFileFilter("*.gradle.kts"),
                TrueFileFilter.INSTANCE);
        files.addAll(gradleFiles);
        files.addAll(kotlinFiles);

        for (String plugin : plugins) {

            String configTask = SUPPORTED_PLUGINS.get(plugin);
            if (plugin.matches(".signing.")) {
                configTask = "signing";
            } else if (configTask == null) {
                throw new ManipulationException("No support for removing plugin {}", plugin);
            }

            for (File buildFile : files) {
                try {
                    List<String> lines = org.apache.commons.io.FileUtils.readLines(buildFile, Charset.defaultCharset());
                    String eol;
                    try {
                        eol = FileIO.determineEOL(buildFile).value();
                    } catch (ManipulationException e) {
                        logger.warn("Unable to determine EOL for {}", buildFile);
                        eol = "\n";
                    }

                    boolean removed = lines.removeIf(i -> i.contains(plugin));

                    String content = String.join(eol, lines);
                    Matcher m = Pattern.compile("(^|\\s)+" + configTask).matcher(content);
                    int startIndex = m.find() ? m.start() : -1;

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

                        removed = true;
                    }

                    if (removed) {
                        logger.debug("Removed instances of plugin {} with configuration block of {} from {}", plugin,
                                configTask, buildFile);
                        FileUtils.writeStringToFile(buildFile, content, Charset.defaultCharset());
                    }
                } catch (IOException e) {
                    throw new ManipulationException("Unable to read build file {}", buildFile, e);
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
