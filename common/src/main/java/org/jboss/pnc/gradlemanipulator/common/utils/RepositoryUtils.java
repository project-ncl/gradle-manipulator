package org.jboss.pnc.gradlemanipulator.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;

/**
 * Utilities for analysing repository declarations in Gradle scripts.
 */
@UtilityClass
public class RepositoryUtils {
    private final Logger logger = GMLogger.getLogger(RepositoryUtils.class);

    private final Pattern URL_PATTERN = Pattern.compile("url\\s*[=:]?\\s*[\"']([^\"']+)[\"']");

    private final Pattern NAME_PATTERN = Pattern.compile("name\\s*[=:]?\\s*[\"']([^\"']+)[\"']");

    /**
     * URL of the Gradle Central Plugin Repository, as returned by {@code RepositoryHandler.gradlePluginPortal()}
     * (stored without a trailing slash).
     */
    private final String GRADLE_PLUGIN_PORTAL_URL = "https://plugins.gradle.org/m2";

    /**
     * Matches {@code gradlePluginPortal()}, {@code gradlePluginPortal(...)} and {@code gradlePluginPortal { ... }}.
     */
    private final Pattern GRADLE_PLUGIN_PORTAL_PATTERN = Pattern.compile("\\bgradlePluginPortal\\s*[({]");

    /**
     * URLs of the other well-known {@code RepositoryHandler} shortcuts ({@code mavenCentral()} and
     * {@code google()}), stored without a trailing slash. These are never excluded, whether an init script
     * declares them via the shortcut or spells out the URL explicitly, as (unlike the plugin portal) nothing
     * guarantees the build environment provides them.
     *
     * @see <a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.RepositoryHandler.html">
     *      RepositoryHandler</a>
     */
    private final Set<String> RETAINED_WELL_KNOWN_URLS = Collections.unmodifiableSet(
            new HashSet<>(
                    Arrays.asList("https://repo.maven.apache.org/maven2", "https://dl.google.com/dl/android/maven2")));

    /**
     * Reads each init script and extracts repository URLs. This covers:
     * <ul>
     * <li>Explicitly declared {@code url = "..."} / {@code url "..."} values, except for the Maven Central and
     * Google URLs (see {@link #RETAINED_WELL_KNOWN_URLS}) so that explicit URLs behave the same as
     * {@code mavenCentral()} / {@code google()}</li>
     * <li>{@code gradlePluginPortal()} calls — mapped to its well-known URL
     * {@code https://plugins.gradle.org/m2}.
     * <p>
     * <b>Note:</b> Even if the project's own {@code build.gradle} also declares
     * {@code gradlePluginPortal()} (or the equivalent explicit URL), it is intentionally excluded from
     * {@code repositories-backup.xml} as PNC {@code repository-driver} always adds that remote for every Gradle
     * build so it is not required.</li>
     * </ul>
     * URLs are returned without a trailing slash.
     *
     * @param initScripts init script files to analyse
     * @return immutable set of repository URLs
     */
    public Set<String> collectInitScriptRepositoryUrls(List<File> initScripts) {
        if (initScripts.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> urls = new LinkedHashSet<>();
        for (File initScript : initScripts) {
            logger.info("Analysing init script for repository URLs: {}", initScript.getAbsolutePath());
            try {
                String content = FileUtils.readFileToString(initScript, StandardCharsets.UTF_8);
                Matcher m = URL_PATTERN.matcher(content);
                while (m.find()) {
                    String url = m.group(1);
                    url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                    if (RETAINED_WELL_KNOWN_URLS.contains(url)) {
                        logger.debug("Not excluding well-known repository URL {} from {}", url, initScript);
                    } else {
                        urls.add(url);
                    }
                }
                if (GRADLE_PLUGIN_PORTAL_PATTERN.matcher(content).find()) {
                    urls.add(GRADLE_PLUGIN_PORTAL_URL);
                }
            } catch (IOException e) {
                logger.warn(
                        "Could not read init script '{}' for repository analysis: {}",
                        initScript.getAbsolutePath(),
                        e.getMessage());
            }
        }
        return Collections.unmodifiableSet(urls);
    }

    /**
     * Reads each init script and extracts explicitly declared repository names
     * ({@code name = "..."} / {@code name "..."}).
     *
     * @param initScripts init script files to analyse
     * @return immutable set of repository names
     */
    public Set<String> collectInitScriptRepositoryNames(List<File> initScripts) {
        if (initScripts.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<>();
        for (File initScript : initScripts) {
            try {
                String content = FileUtils.readFileToString(initScript, StandardCharsets.UTF_8);
                Matcher m = NAME_PATTERN.matcher(content);
                while (m.find()) {
                    names.add(m.group(1));
                }
            } catch (IOException e) {
                // warning already logged by collectInitScriptRepositoryUrls for the same file
            }
        }
        return Collections.unmodifiableSet(names);
    }
}
