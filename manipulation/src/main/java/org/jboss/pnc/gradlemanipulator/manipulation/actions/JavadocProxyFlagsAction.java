package org.jboss.pnc.gradlemanipulator.manipulation.actions;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;

/**
 * Injects HTTP/HTTPS proxy system properties as {@code jFlags} on every Javadoc task so
 * that the forked {@code javadoc} JVM can reach external URLs through the proxy.
 *
 * <p>
 * The {@code javadoc} tool runs in a child JVM and does not inherit the parent JVM's
 * proxy system properties automatically (see
 * <a href="https://issues.gradle.org/browse/GRADLE-1228">GRADLE-1228</a>). This action
 * forwards every non-blank proxy property as a {@code -D} flag, which is the standard way
 * to pass JVM arguments to the {@code javadoc} tool.
 *
 * <p>
 * Compatible with Gradle 4.10 – 9.x and with both Groovy and Kotlin DSL projects.
 * No reflection is required: {@link Javadoc} and {@link MinimalJavadocOptions} have been
 * stable across the full version range.
 */
public class JavadocProxyFlagsAction implements Action<Project> {

    /**
     * The proxy-related system property names to forward, in declaration order.
     * Each property is only forwarded when it is non-blank at the time of configuration.
     */
    private static final String[] PROXY_PROPERTIES = {
            "http.proxyHost",
            "http.proxyPort",
            "http.proxyUser",
            "http.proxyPassword",
            "https.proxyHost",
            "https.proxyPort",
            "http.nonProxyHosts",
    };

    private final Logger logger = GMLogger.getLogger(getClass());

    /**
     * Applies proxy {@code jFlags} to all {@link Javadoc} tasks in the given project.
     * Tasks that have already had a given flag set are skipped for that flag, making this
     * action idempotent across repeated {@code afterEvaluate} calls.
     *
     * @param project the project whose Javadoc tasks are to be configured
     */
    @Override
    public void execute(Project project) {
        List<String> flags = new ArrayList<>();
        for (String key : PROXY_PROPERTIES) {
            String value = System.getProperty(key);
            if (isNotEmpty(value)) {
                flags.add("-D" + key + "=" + value);
            }
        }

        if (flags.isEmpty()) {
            logger.debug(
                    "No proxy system properties found; skipping Javadoc jFlags injection for project '{}'",
                    project.getName());
            return;
        }

        // withType(Javadoc.class).configureEach(…) is the lazy API (Gradle 4.9+).
        // It handles zero, one, or many Javadoc tasks without throwing when none exist.
        project.getTasks().withType(Javadoc.class).configureEach(task -> {
            MinimalJavadocOptions options = task.getOptions();
            // getJFlags() returns the current list; copy it to avoid aliasing issues.
            List<String> existing = new ArrayList<>(options.getJFlags());
            for (String flag : flags) {
                if (!existing.contains(flag)) {
                    existing.add(flag);
                    logger.info(
                            "Javadoc task '{}' in project '{}': adding proxy jFlag '{}'",
                            task.getName(),
                            project.getName(),
                            flag);
                }
            }
            options.jFlags(existing.toArray(new String[0]));
        });
    }
}
