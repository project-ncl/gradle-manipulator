package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.commonjava.maven.atlas.ident.util.VersionUtils;
import org.commonjava.maven.atlas.ident.version.InvalidVersionSpecificationException;
import org.commonjava.maven.atlas.ident.version.VersionSpec;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.FileIO;
import org.slf4j.Logger;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

public class PluginUtils {
    private static final Map<String, PluginReference> SUPPORTED_PLUGINS = new LinkedHashMap<>();

    public static final String SEMANTIC_BUILD_VERSIONING = "net.vivin.gradle-semantic-build-versioning";

    static {
        // Pair left is configuration, right is tasks
        SUPPORTED_PLUGINS.put("com.github.ben-manes.versions", new PluginReference("dependencyUpdates", "",
                Collections.emptySet()));
        SUPPORTED_PLUGINS.put("com.github.burrunan.s3-build-cache",
                new PluginReference("buildCache", "", Collections.emptySet()));
        SUPPORTED_PLUGINS.put("de.marcphilipp.nexus-publish",
                new PluginReference("nexusPublishing", "", Collections.singleton("publishToSonatype")));
        SUPPORTED_PLUGINS.put("gradle-enterprise", new PluginReference("gradleEnterprise", "", Collections.emptySet()));
        SUPPORTED_PLUGINS.put("com.gradle.enterprise", new PluginReference("gradleEnterprise", "", Collections.emptySet()));
        SUPPORTED_PLUGINS.put("com.gradle.common-custom-user-data-gradle-plugin", new PluginReference("", "",
                Collections.emptySet()));
        SUPPORTED_PLUGINS.put("io.codearte.nexus-staging",
                new PluginReference("nexusStaging",
                        "",
                        Stream.of("closeRepository", "releaseRepository", "closeAndReleaseRepository").collect(
                                Collectors.toSet())));
        SUPPORTED_PLUGINS.put("io.github.gradle-nexus.publish-plugin",
                new PluginReference("nexusPublishing", "", Collections.emptySet()));
        SUPPORTED_PLUGINS.put("nebula.publish-verification",
                new PluginReference("nebulaPublishVerification", "", Collections.emptySet()));
        SUPPORTED_PLUGINS.put("signing", new PluginReference("signing", "SigningPlugin", Collections.emptySet()));
        SUPPORTED_PLUGINS.put(SEMANTIC_BUILD_VERSIONING, new PluginReference("preRelease", "", Collections.emptySet()));
    }

    @Setter
    @RequiredArgsConstructor
    public static class PluginReference {
        private final String configBlock;
        private final String type;
        private final Set<String> tasks;
    }

    public enum DokkaVersion {
        NONE(null),
        MINIMUM("0.9.18"),
        TEN("0.10.0"),
        POST_ONE("1.4.0");

        final VersionSpec version;

        DokkaVersion(String s) {
            this.version = s == null ? null : VersionUtils.createSingleVersion(s);
        }

        public static DokkaVersion parseVersion(String version)
                throws ManipulationException {
            VersionSpec v;
            try {
                v = VersionUtils.createSingleVersion(version);
            } catch (InvalidVersionSpecificationException e) {
                throw new ManipulationException("Unable to parse version: {}", version);
            }
            if (MINIMUM.version.compareTo(v) >= 0) {
                return MINIMUM;
            } else if (v.compareTo(POST_ONE.version) >= 0) {
                return POST_ONE;
            }
            return TEN;
        }
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
            plugins.add("\"signing\"");
            plugins.add("'signing'");
        }

        for (String plugin : plugins) {
            PluginReference pluginReference = SUPPORTED_PLUGINS.get(plugin);
            Set<String> tasks;
            String configBlock;
            String pluginType;

            if (plugin.matches(".signing.")) {
                configBlock = "signing";
                tasks = Collections.emptySet();
                pluginType = "SigningPlugin";
            } else if (pluginReference == null) {
                throw new ManipulationException("No support for removing plugin {}", plugin);
            } else {
                configBlock = pluginReference.configBlock;
                tasks = pluginReference.tasks;
                pluginType = pluginReference.type;
            }

            final Collection<File> files = FileUtils.listFiles(target,
                    new WildcardFileFilter("*.gradle", "*.gradle.kts"), TrueFileFilter.INSTANCE);

            for (File buildFile : files) {
                try {
                    List<String> lines = org.apache.commons.io.FileUtils.readLines(buildFile, Charset.defaultCharset());
                    String eol = getEOL(logger, buildFile);

                    // Remove the plugin
                    boolean removed = lines.removeIf(i -> i.contains(plugin) && !i.contains("plugins.withId"));
                    // This handles the scenario, often in Kotlin build files where the plugin may be just
                    // its name i.e. signing without any quotes or brackets
                    removed |= lines.removeIf(i -> i.matches(".*\\s+" +
                            plugin.replace("\"", "") + "(\\s|$)+.*")
                            && !i.contains("{"));

                    // Remove any task references.
                    // TODO: Handle if the task reference spans multiple lines
                    for (String t : Objects.requireNonNull(tasks)) {
                        removed |= lines.removeIf(i -> i.contains(t));
                    }

                    String content = String.join(eol, lines);

                    if (isNotEmpty(configBlock)) {
                        // Remove any configuration block
                        StringBuilder contentBuilder = new StringBuilder(content);
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder, "(^|\\s)+" + configBlock +
                                "(\\s|$)+");
                        content = contentBuilder.toString();
                    }

                    if (isNotEmpty(pluginType)) {
                        StringBuilder contentBuilder = new StringBuilder(content);
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                                "plugins.withType<" + pluginType + ">");
                        content = contentBuilder.toString();
                    }

                    // This is to remove blocks like plugins.withId("de.marcphilipp.nexus-publish") { ... }
                    StringBuilder contentBuilder = new StringBuilder(content);
                    removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                            "plugins.withId\\(\"" + plugin + "\"\\)");
                    content = contentBuilder.toString();

                    if (removed) {
                        logger.info("Removed instances of plugin {} with configuration block of {} from {}", plugin,
                                configBlock, buildFile);
                        FileUtils.writeStringToFile(buildFile, content, Charset.defaultCharset());
                    }
                } catch (IOException e) {
                    throw new ManipulationException("Unable to read build file {}", buildFile, e);
                }
            }
        }
    }

    /**
     * This accessor function allow a Groovy script to retrieve the currently supported
     * list of plugins that may be removed and alter it dynamically.
     *
     * @return a map of supported plugins
     */
    public static Map<String, PluginReference> getSupportedPlugins() {
        return SUPPORTED_PLUGINS;
    }

    public static boolean checkForSemanticBuildVersioning(Logger logger, File target)
            throws ManipulationException {
        File settingsFile = new File(target, "settings.gradle");
        if (settingsFile.exists()) {
            try {
                List<String> lines = FileUtils.readLines(settingsFile, Charset.defaultCharset());
                Pattern p = Pattern.compile("^((?!^\\s*//).*" + Pattern.quote(SEMANTIC_BUILD_VERSIONING) + ".*)$");
                for (String s : lines) {
                    if (p.matcher(s).matches()) {
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

    public static void addLenientLockMode(Logger logger, File target)
            throws ManipulationException {
        final String depLock = "dependencyLocking {";
        final Collection<File> files = FileUtils.listFiles(target, new WildcardFileFilter("*.gradle", "*.gradle.kts"),
                TrueFileFilter.INSTANCE);

        for (File buildFile : files) {
            boolean removed = false;
            try {
                List<String> lines = org.apache.commons.io.FileUtils.readLines(buildFile, Charset.defaultCharset());

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.contains(depLock)) {
                        removed = true;
                        if (buildFile.toString().endsWith("kts")) {
                            line = line.replace(depLock, depLock + "\n lockMode.set(LockMode.LENIENT) ");
                        } else {
                            line = line.replace(depLock, depLock + "\n lockMode = LockMode.LENIENT");
                        }
                        lines.set(i, line);
                    }
                }
                if (removed) {
                    String content = String.join(getEOL(logger, buildFile), lines);
                    logger.debug("Added LENIENT lockMode to {}", buildFile);
                    FileUtils.writeStringToFile(buildFile, content, Charset.defaultCharset());
                }
            } catch (IOException e) {
                throw new ManipulationException("Unable to read build file {}", buildFile, e);
            }
        }
    }

    private static String getEOL(Logger logger, File buildFile) {
        String eol;
        try {
            eol = FileIO.determineEOL(buildFile).value();
        } catch (ManipulationException e) {
            logger.warn("Unable to determine EOL for {}", buildFile);
            eol = "\n";
        }
        return eol;
    }

    private static boolean removeBlock(Logger logger, File buildFile, String eol, StringBuilder content, String block)
            throws ManipulationException {
        boolean removed = false;

        Pattern pattern = Pattern.compile(block);
        Matcher m = pattern.matcher(content);

        while (m.find()) {
            int startIndex = m.start();
            int endIndex = m.end();
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
                        "Unable to locate block {} to remove within {}", block,
                        buildFile);
            }
            logger.debug("Removing plugin block of {}",
                    content.substring(startIndex, endIndex));
            // The string split/join can lose the trailing new line so force append it.
            content.append(eol);
            content.delete(startIndex, endIndex);
            m = pattern.matcher(content);
            removed = true;
        }
        return removed;
    }
}
