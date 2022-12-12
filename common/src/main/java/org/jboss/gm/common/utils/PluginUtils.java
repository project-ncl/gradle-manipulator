package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final Map<String, PluginReference> PLUGINS = new LinkedHashMap<>();

    public static final String SEMANTIC_BUILD_VERSIONING = "net.vivin.gradle-semantic-build-versioning";

    static {
        PLUGINS.put("com.github.ben-manes.versions", new PluginReference(Collections.singleton("dependencyUpdates"),
                "", Collections.singleton("DependencyUpdatesTask"),
                "", Collections.singleton("com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask")));
        PLUGINS.put("com.github.burrunan.s3-build-cache", new PluginReference("buildCache"));
        PLUGINS.put("de.marcphilipp.nexus-publish",
                new PluginReference(Collections.singleton("nexusPublishing"), "", Collections.singleton(
                        "publishToSonatype"),
                        "NexusPublishExtension",
                        Collections.singleton("de.marcphilipp.gradle.nexus.NexusPublishExtension")));
        PLUGINS.put("gradle-enterprise", new PluginReference("gradleEnterprise"));
        PLUGINS.put("com.gradle.enterprise", new PluginReference(Stream.of("gradleEnterprise", "buildScan").collect(
                Collectors.toSet()), "", null));
        // Following two require Gradle Enterprise plugin so remove these as well.
        PLUGINS.put("io.spring.ge.conventions", new PluginReference(null));
        PLUGINS.put("com.gradle.common-custom-user-data-gradle-plugin", new PluginReference(null));
        PLUGINS.put("io.codearte.nexus-staging", new PluginReference(Collections.singleton("nexusStaging"), "",
                Stream.of("closeRepository", "releaseRepository", "closeAndReleaseRepository").collect(
                        Collectors.toSet())));
        PLUGINS.put("io.github.gradle-nexus.publish-plugin", new PluginReference("nexusPublishing"));
        PLUGINS.put("nebula.publish-verification", new PluginReference("nebulaPublishVerification"));
        PLUGINS.put("signing", new PluginReference(Collections.singleton("signing"), "SigningPlugin", null));
        PLUGINS.put(SEMANTIC_BUILD_VERSIONING, new PluginReference("preRelease"));
    }

    /**
     * This class encapsulates the different methods plugins can appear and therefore how they can be removed
     */
    @Setter
    @RequiredArgsConstructor
    public static class PluginReference {

        public PluginReference(String configBlock) {
            this.configBlocks = configBlock == null ? Collections.emptySet() : Collections.singleton(configBlock);
            type = "";
            tasks = Collections.emptySet();
            configureExtension = "";
            imports = Collections.emptySet();
        }

        public PluginReference(Set<String> configBlock, String type, Set<String> tasks) {
            this.configBlocks = configBlock;
            this.type = type;
            this.tasks = tasks == null ? Collections.emptySet() : tasks;
            configureExtension = "";
            imports = Collections.emptySet();
        }

        private final Set<String> configBlocks;

        // For finding blocks like plugins.withType<SigningPlugin>
        private final String type;

        // Tasks this plugin provides
        private final Set<String> tasks;

        // Configuration extension block e.g. configure<NexusPublishExtension>
        private final String configureExtension;

        // Imports that refer to this plugin
        private final Set<String> imports;
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
     * <p>
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
            plugins.addAll(PLUGINS.keySet());
        } else if (plugins.stream().anyMatch(s -> s.matches("REC.*"))) {
            // Shortcut to represent removing the default selection of the supported plugins
            // This is equivalent to 'ALL' barring removal of the signing plugin
            plugins.clear();
            plugins.addAll(PLUGINS.keySet());
            plugins.remove("signing");
        }

        // As the configuration block is named the same as the plugin we differentiate via the
        // potential quoting mechanism to avoid problems when parsing the file to remove the plugins.
        // This will likely leave in Kotlin build files items like plugins { signing } but that
        // is harmless and has been seen to be required for e.g. opentelemetry-java-instrumentation
        if (plugins.remove("signing")) {
            plugins.add("\"signing\"");
            plugins.add("'signing'");
        }

        for (String plugin : plugins) {
            PluginReference pluginReference = PLUGINS.get(plugin);
            Set<String> tasks;
            Set<String> configBlocks;
            String pluginType;
            String configureExtension;
            Set<String> pluginImports;

            if (plugin.matches(".signing.")) {
                configBlocks = Collections.singleton("signing");
                tasks = Collections.emptySet();
                pluginType = "SigningPlugin";
                pluginImports = Collections.emptySet();
                configureExtension = "SigningExtension";
            } else if (pluginReference == null) {
                throw new ManipulationException("No support for removing plugin {}", plugin);
            } else {
                configBlocks = pluginReference.configBlocks;
                tasks = pluginReference.tasks;
                pluginType = pluginReference.type;
                pluginImports = pluginReference.imports;
                configureExtension = pluginReference.configureExtension;
            }

            final Collection<File> files = FileUtils.listFiles(target,
                    new WildcardFileFilter("*.gradle", "*.gradle.kts"), TrueFileFilter.INSTANCE);

            for (File buildFile : files) {
                try {
                    List<String> lines = org.apache.commons.io.FileUtils.readLines(buildFile, Charset.defaultCharset());
                    String eol = getEOL(logger, buildFile);

                    for (int i = 0; i < lines.size(); i++) {
                        // Special case handling - https://github.com/marcphilipp/nexus-publish-plugin implicitly
                        // applies the maven-publish plugin so apply it manually to avoid breakages
                        if (plugin.equals("de.marcphilipp.nexus-publish") &&
                                lines.get(i).matches("\\s*apply.*" + plugin + ".*")) {
                            logger.debug("Replacing nexus-publish apply plugin with maven-publish");
                            if (buildFile.getName().endsWith(".gradle")) {
                                lines.set(i, "apply plugin: \"maven-publish\"");
                            } else {
                                lines.set(i, "apply(plugin = \"maven-publish\")");
                            }
                            break;
                        }
                    }
                    // Remove the plugin. Plugins can be applied as below with quote variation of ", ', `
                    // id("...")
                    // id "..."
                    // apply plugin: "..."
                    boolean removed = lines.removeIf(i -> i.matches(".*([`\"'])" + plugin + "([`\"']).*") &&
                            !i.contains("plugins.withId"));
                    removed |= lines.removeIf(i -> i.matches(".*\\s+" + plugin + "(\\s|$)+.*")
                            && !i.contains("{"));

                    // Remove any task references.
                    for (String t : tasks) {
                        removed |= lines.removeIf(i -> i.contains(t) && !i.contains("{"));
                    }

                    // Remove any imports.
                    for (String pluginImport : pluginImports) {
                        removed |= lines.removeIf(i -> i.contains("import " + pluginImport));
                    }

                    String content = String.join(eol, lines);
                    StringBuilder contentBuilder;

                    // Remove any configuration block
                    for (String configBlock : configBlocks) {
                        contentBuilder = new StringBuilder(content);
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                                "(?m)(^|project\\.|\\s)+" + configBlock + "(\\s|$)+");
                        content = contentBuilder.toString();
                    }

                    // Remove withType blocks
                    if (isNotEmpty(pluginType)) {
                        contentBuilder = new StringBuilder(content);
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                                "plugins.withType<" + pluginType + ">");
                        content = contentBuilder.toString();
                    }

                    // Remove withId blocks e.g.
                    // plugins.withId("de.marcphilipp.nexus-publish") { ... }
                    contentBuilder = new StringBuilder(content);
                    removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                            "plugins.withId\\(\"" + plugin + "\"\\)");
                    content = contentBuilder.toString();

                    // Remove configure extension blocks e.g.
                    // configure<NexusPublishExtension> {
                    if (isNotEmpty(configureExtension)) {
                        contentBuilder = new StringBuilder(content);
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                                "configure<" + configureExtension + ">");
                        content = contentBuilder.toString();
                    }

                    // Remove any multi-line task references e.g.
                    // rootProject.tasks.named("closeAndReleaseRepository") {
                    for (String t : tasks) {
                        contentBuilder = new StringBuilder(content);
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                                "(?m)^.*\\(\"" + t + "\"\\)");
                        // Sometimes tasks can be single quoted e.g.
                        // tasks.named('closeAndReleaseRepository') {
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                                "(?m)^.*\\('" + t + "'\\)");
                        removed |= removeBlock(logger, buildFile, eol, contentBuilder,
                                "(?m)^.*named<" + t + ">.*?\\s");
                        content = contentBuilder.toString();
                    }

                    if (removed) {
                        logger.info("Removed instances of plugin {} with configuration block of {} from {}", plugin,
                                String.join(",", configBlocks), buildFile);
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
        return PLUGINS;
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
            while (Character.isWhitespace(content.charAt(endIndex)) && content.charAt(endIndex) != '{') {
                endIndex++;
            }
            if (content.charAt(endIndex) != '{') {
                // We couldn't find a configuration block. This might be something embedded in a comment or a Kotlin
                // plugin enablement e.g.
                // plugins { signing }
                continue;
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
