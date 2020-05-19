package org.jboss.gm.cli;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.gradle.internal.Pair;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.utils.GroovyUtils;
import org.jboss.gm.common.utils.ManifestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;
import ch.qos.logback.classic.Level;

@SuppressWarnings("unused")
@Command(name = "GradleAnalyser",
        description = "CLI to optionally run Groovy scripts and then invoke Gradle.",
        mixinStandardHelpOptions = true, // add --help and --version options
        versionProvider = ManifestVersionProvider.class)
public class Main implements Callable<Void> {

    private final GradleConnector connector = GradleConnector.newConnector();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @SuppressWarnings({ "FieldCanBeLocal", "FieldMayBeFinal" })
    @Option(names = "--no-colour",
            negatable = true,
            description = "Enable (or disable with '--no-colour') colour output on logging.")
    private boolean colour = true;

    @Option(names = "-d", description = "Enable debug.")
    private boolean debug;

    @Option(names = "--trace", description = "Enable trace.")
    private boolean trace;

    @Option(names = { "-t", "--target" }, required = true, description = "Target Gradle directory.")
    private File target;

    @SuppressWarnings({ "MismatchedQueryAndUpdateOfCollection", "FieldMayBeFinal" })
    @Option(names = "-D", description = "Pass supplemental arguments (e.g. groovy script commands)")
    private Map<String, String> jvmPropertyParams = new LinkedHashMap<>();

    @Option(names = "-l", description = "Location of Gradle installation.")
    private File installation;

    @Unmatched
    private List<String> gradleArgs;

    // Partial workaround for https://github.com/gradle/gradle/issues/3117
    // It may still be necessary on Gradle < 5.3 to do ' LC_ALL=en_US LANG=en_US java -jar '
    private final Map<String, String> envVars = Stream.of(
            Pair.of("LC_ALL", "en_US"),
            Pair.of("LANG", "en_US"),
            Pair.of("PROMPT", "$"),
            Pair.of("RPROMPT", ""),
            // Also add the System PATH so external executables may be found.
            Pair.of("PATH", System.getenv("PATH"))).collect(Collectors.toMap(Pair::left, Pair::right));

    /**
     * This allows the tool to be invoked. The command line parsing allows for:
     *
     * <p>
     * {@code
     * <cli-tool> [-d] [-l <location>]  -t <target>   ...gradle arguments... -Dkey-value
     * }
     *
     * @param args Arguments to the process
     * @throws Exception if an error occurs.
     */
    public static void main(String[] args) throws Exception {
        Main m = new Main();
        System.exit(m.run(args));
    }

    public int run(String[] args) throws Exception {
        CommandLine cl = new CommandLine(this);
        ExceptionHandler handler = new ExceptionHandler();

        cl.setExecutionStrategy(new CommandLine.RunAll());
        cl.setExecutionExceptionHandler(handler);
        cl.setOverwrittenOptionsAllowed(true);

        int result = cl.execute(args);
        if (handler.getException() != null) {
            throw handler.getException();
        }
        return result;
    }

    private void executeGradle() throws ManipulationException {

        if (installation != null) {
            if (!installation.exists()) {
                throw new ManipulationException("Unable to locate Gradle installation at {}", installation);
            }
            connector.useInstallation(installation);
        } else {
            connector.useBuildDistribution();
        }
        // Set the timeout to a low value (default is 3 minutes) so that it expires quickly.
        if (connector instanceof DefaultGradleConnector) {
            DefaultGradleConnector dgc = ((DefaultGradleConnector) connector);
            dgc.daemonMaxIdleTime(10, TimeUnit.SECONDS);

            if (trace) {
                dgc.setVerboseLogging(true);
            }
            else if (debug) {
                // If debug has been enabled in the CLI propagate that through as info (we have customised logging).
                gradleArgs.add("--info");
            }
        }

        try (ProjectConnection connection = connector.connect()) {
            BuildLauncher build = connection.newBuild();
            Set<String> jvmArgs = jvmPropertyParams.entrySet().stream()
                    .map(entry -> "-D" + entry.getKey() + '=' + entry.getValue())
                    .collect(Collectors.toSet());

            if (colour) {
                build.setColorOutput(true);
            } else {
                jvmArgs.add("-DloggingColours=false");
            }

            logger.info("Executing CLI {} on Gradle project {} with JVM args '{}' and arguments '{}'",
                    ManifestUtils.getManifestInformation(), target, jvmArgs, gradleArgs);

            build.setEnvironmentVariables(envVars);
            build.setJvmArguments(jvmArgs);
            build.withArguments(gradleArgs);
            build.setStandardOutput(System.out);
            build.setStandardError(System.err);
            build.run();
        } catch (BuildException e) {
            logger.error("Caught exception running build", e.getCause());
            throw new ManipulationException("Caught exception running build", e.getCause());
        }
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     */
    @Override
    public Void call() throws ManipulationException {
        final Logger rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) rootLogger;
        final Configuration configuration = ConfigCache.getOrCreate(Configuration.class);

        if (debug) {
            root.setLevel(Level.DEBUG);
        }

        if (target == null || !target.exists()) {
            throw new ManipulationException("Unable to locate target directory {}", target);
        }

        if (!jvmPropertyParams.isEmpty()) {

            // By passing the command line into the configuration object have a standard place to retrieve
            // the configuration which makes the underlying code simpler.
            System.getProperties().putAll(jvmPropertyParams);
            configuration.reload();
            logger.debug("Configuration reloaded with {}", configuration.dumpCurrentConfig());

            GroovyUtils.runCustomGroovyScript(logger, InvocationStage.FIRST, target, configuration, null, null);
        }

        connector.forProjectDirectory(target);

        if ("true".equals(jvmPropertyParams.get("manipulation.disable"))) {
            logger.info("Gradle Manipulator disabled");
        } else {
            logger.debug("Executing Gradle");
            executeGradle();
        }

        return null;
    }

    @Getter
    private static class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {

        private Exception exception;

        /**
         * Handles an {@code Exception} that occurred while executing the {@code Runnable} or
         * {@code Callable} command and returns an exit code suitable for returning from execute.
         *
         * @param ex the Exception thrown by the {@code Runnable}, {@code Callable} or {@code Method} user object of the command
         * @param commandLine the CommandLine representing the command or subcommand where the exception occurred
         * @param parseResult the result of parsing the command line arguments
         * @return an exit code
         */
        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
            this.exception = ex;
            return 1;
        }
    }
}