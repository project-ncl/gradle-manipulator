package org.jboss.gm.cli;

import java.io.File;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.gradle.internal.Pair;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.utils.GroovyUtils;
import org.jboss.gm.common.utils.JavaUtils;
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
    private static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("4.10");

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

    private File verifyBuildEnvironment(ProjectConnection connection) throws ManipulationException {
        try {
            BuildEnvironment env = connection.getModel(BuildEnvironment.class);
            String versionString = env.getGradle().getGradleVersion();
            GradleVersion version = GradleVersion.version(versionString);

            logger.info("Gradle version: {}", versionString);
            logger.info("Java home: {}", JavaUtils.getJavaHome());
            logger.info("JVM arguments: {}", env.getJava().getJvmArguments());

            if (version.compareTo(MIN_GRADLE_VERSION) < 0) {
                throw new ManipulationException("{} is too old and is unsupported. You need at least {}.", version,
                        MIN_GRADLE_VERSION);
            }

            return env.getJava().getJavaHome();
        } catch (GradleConnectionException e) {
            Throwable firstCause = e.getCause();

            if (firstCause != null) {
                Throwable secondCause = firstCause.getCause();

                if (secondCause != null && secondCause.getClass() == IllegalArgumentException.class) {
                    String causeMessage = secondCause.getMessage();

                    if (causeMessage != null) {
                        Pattern pattern = Pattern.compile("^Could not determine java version from '(?<version>.*)'.$");
                        Matcher matcher = pattern.matcher(causeMessage);

                        if (matcher.matches()) {
                            String version = matcher.group("version");
                            logger.debug("Caught exception processing Gradle API", e);
                            throw new ManipulationException("Java {} is incompatible with Gradle version used to build",
                                    version);
                        }
                    }
                }
            }

            throw e;
        }
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
            } else if (debug) {
                // If debug has been enabled in the CLI propagate that through as info (we have customised logging).
                // Insert it at the start to allow overrides (e.g. for debugging)
                gradleArgs.add(0, "--info");
            }
        }

        try (ProjectConnection connection = connector.connect()) {
            File javaHome = verifyBuildEnvironment(connection);

            if (!JavaUtils.compareJavaHome(javaHome)) {
                // Gradle handles detecting the location in GRADLE_JAVA_HOME. If it doesn't match
                // what it has detected that is an internal error but should never happen.
                logger.info("Java home overridden to: {}", javaHome.getAbsolutePath());
            }
            envVars.put("JAVA_HOME", javaHome.getAbsolutePath());

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
        } catch (GradleConnectionException e) {
            // Unable to do instanceof comparison due to different classloader
            if (e.getCause().getClass().getName().equals("org.gradle.api.UncheckedIOException")) {
                logger.debug("Hit https://github.com/gradle/gradle/issues/9339 ", e);
                logger.error(
                        "Build exception but unable to transfer message due to mix of JDK versions. Examine log for problems");
            } else {
                logger.error("Gradle connection exception", e);
            }
            throw new ManipulationException("Problem executing build");
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
        final Configuration configuration = ConfigCache.getOrCreate(Configuration.class);

        if (debug && rootLogger instanceof ch.qos.logback.classic.Logger) {
            ((ch.qos.logback.classic.Logger) rootLogger).setLevel(Level.DEBUG);
        }

        if (!target.isAbsolute()) {
            target = new File(Paths.get("").toAbsolutePath().toFile(), target.toString());
            logger.debug("Relative path detected ; resetting to {}", target);
        }
        if (!target.exists()) {
            throw new ManipulationException("Unable to locate target directory {}", target);
        } else if (!target.isDirectory()) {
            throw new ManipulationException("Pass project root as directory not file : {}", target);
        }

        if (!jvmPropertyParams.isEmpty()) {
            // By passing the command line into the configuration object have a standard place to retrieve
            // the configuration which makes the underlying code simpler.
            System.getProperties().putAll(jvmPropertyParams);
            configuration.reload();

            if (logger.isDebugEnabled()) {
                logger.debug("Configuration reloaded with {}", configuration.dumpCurrentConfig());
            }

            GroovyUtils.runCustomGroovyScript(logger, InvocationStage.FIRST, target, configuration, null, null);
        }

        connector.forProjectDirectory(target);

        if (configuration.disableGME()) {
            logger.info("Gradle Manipulator disabled");
        } else {
            logger.debug("Executing Gradle");
            executeGradle();
        }

        return null;
    }

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

        public Exception getException() {
            if (exception != null) {
                if ("Caught exception running build".equals(exception.getMessage())) {
                    exception = (Exception) exception.getCause();
                }
                if ("org.gradle.internal.exceptions.LocationAwareException".equals(exception.getClass().getName())) {
                    exception = (Exception) exception.getCause();
                }
            }
            return exception;
        }
    }
}
