package org.jboss.gm.cli;

import static org.jboss.gm.common.utils.PluginUtils.SEMANTIC_BUILD_VERSIONING;

import ch.qos.logback.classic.Level;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.aeonbits.owner.ConfigCache;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.util.ManifestUtils;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.GradleEnvironment;
import org.gradle.tooling.model.build.JavaEnvironment;
import org.gradle.util.GradleVersion;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.utils.GroovyUtils;
import org.jboss.gm.common.utils.JavaUtils;
import org.jboss.gm.common.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;

@SuppressWarnings("unused")
@Command(
        name = "GradleAnalyser",
        description = "CLI to optionally run Groovy scripts and then invoke Gradle.",
        mixinStandardHelpOptions = true, // add --help and --version options
        versionProvider = ManifestVersionProvider.class)
public class Main implements Callable<Void> {
    private static final GradleVersion MIN_GRADLE_VERSION = GradleVersion.version("4.10");

    private static final GradleVersion MIN_GRADLE_VERSION_GRADLE_ISSUE_3117 = GradleVersion.version("5.3");

    private static final List<GradleVersion> BROKEN_GRADLE_VERSIONS = Arrays.asList(
            GradleVersion.version("8.10"),
            GradleVersion.version("8.10.1"));

    private final GradleConnector connector = GradleConnector.newConnector();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @SuppressWarnings({ "FieldCanBeLocal", "FieldMayBeFinal" })
    @Option(
            names = "--no-colour",
            negatable = true,
            description = "Enable (or disable with '--no-colour') colour output on logging.")
    private boolean colour = true;

    @Option(names = "-d", description = "Enable debug.")
    private boolean debug;

    @Option(names = "--trace", description = "Enable trace.")
    private boolean trace;

    @Option(
            names = { "-t", "--target" },
            description = "Target Gradle directory (default: ${DEFAULT-VALUE}).",
            defaultValue = "${sys:user.dir}")
    private File target;

    @SuppressWarnings({ "MismatchedQueryAndUpdateOfCollection", "FieldMayBeFinal" })
    @Option(names = "-D", description = "Pass supplemental arguments (e.g. groovy script commands)")
    private Map<String, String> jvmPropertyParams = new LinkedHashMap<>();

    @SuppressWarnings({ "MismatchedQueryAndUpdateOfCollection", "FieldMayBeFinal" })
    @Option(names = "-e", description = "Pass supplemental environment variables ")
    private Map<String, String> envParams = new LinkedHashMap<>();

    @Option(names = "-l", description = "Location of Gradle installation.")
    private File installation;

    @Unmatched
    private List<String> gradleArgs;

    private final Map<String, String> envVars = new HashMap<>(System.getenv());

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
        if (handler.hasException()) {
            throw handler.getException();
        }
        return result;
    }

    private JavaEnvironment verifyBuildEnvironment(BuildEnvironment buildEnvironment, GradleVersion version)
            throws ManipulationException {
        try {
            JavaEnvironment javaEnvironment = buildEnvironment.getJava();

            logger.info("Gradle version: {}", version.getVersion());
            logger.info("Java home: {}", JavaUtils.getJavaHome());
            logger.info("JVM arguments: {}", javaEnvironment.getJvmArguments());

            if (version.compareTo(MIN_GRADLE_VERSION) < 0) {
                throw new ManipulationException(
                        "{} is too old and is unsupported. You need at least {}.",
                        version,
                        MIN_GRADLE_VERSION);
            }
            if (BROKEN_GRADLE_VERSIONS.contains(version)) {
                logger.error("{} is a known broken version. Update to 8.10.2.", version);
                throw new ManipulationException("{} is a known broken version. Update to 8.10.2.", version);
            }

            return javaEnvironment;
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
                            String javaVersion = matcher.group("version");
                            logger.debug("Caught exception processing Gradle API", e);
                            throw new ManipulationException(
                                    "Java {} is incompatible with Gradle version used to build",
                                    javaVersion);
                        }
                    }
                }
            }

            throw e;
        }
    }

    private void executeGradle(OutputStream out, boolean quiet, List<String> currentGradleArgs)
            throws ManipulationException {
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

            if (quiet) {
                currentGradleArgs.add("--quiet");
            } else if (trace) {
                dgc.setVerboseLogging(true);
            } else if (debug) {
                // If debug has been enabled in the CLI propagate that through as info (we have customised logging).
                // Insert it at the start to allow overrides (e.g. for debugging)
                currentGradleArgs.add(0, "--info");
            }
        }

        try (ProjectConnection connection = connector.connect()) {
            BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
            GradleEnvironment gradleEnvironment = buildEnvironment.getGradle();
            String versionString = gradleEnvironment.getGradleVersion();
            GradleVersion gradleVersion = GradleVersion.version(versionString);
            JavaEnvironment javaEnvironment = verifyBuildEnvironment(buildEnvironment, gradleVersion);
            File javaHome = javaEnvironment.getJavaHome();

            if (!JavaUtils.compareJavaHome(javaHome)) {
                // Gradle handles detecting the location in GRADLE_JAVA_HOME. If it doesn't match
                // what it has detected that is an internal error but should never happen.
                logger.info("Java home overridden to: {}", javaHome.getAbsolutePath());
            }

            envVars.put("JAVA_HOME", javaHome.getAbsolutePath());

            BuildLauncher build = connection.newBuild();
            List<String> jvmArgs = jvmPropertyParams.entrySet()
                    .stream()
                    .map(entry -> "-D" + entry.getKey() + '=' + entry.getValue())
                    .collect(Collectors.toCollection(ArrayList::new));
            jvmArgs.addAll(
                    ManagementFactory.getRuntimeMXBean()
                            .getInputArguments()
                            .stream()
                            .filter(s -> s.startsWith("-Xdebug") || s.startsWith("-Xrunjdwp"))
                            .collect(Collectors.toList()));

            // Can't use build.addJvmArguments as it wasn't fixed till 8.13
            // https://github.com/gradle/gradle/issues/31462
            // https://github.com/gradle/gradle/issues/25155
            Collections.addAll(jvmArgs, javaEnvironment.getJvmArguments().toArray(new String[0]));

            if (!quiet && colour && StringUtils.isEmpty(System.getenv("NO_COLOR"))) {
                build.setColorOutput(true);
            } else {
                jvmArgs.add("-DloggingColours=false");
            }
            if (StringUtils.isNotEmpty(System.getenv("JAVA_OPTS"))) {
                Collections.addAll(jvmArgs, System.getenv("JAVA_OPTS").split("\\s+"));
            }
            if (!envParams.isEmpty()) {
                envVars.putAll(envParams);
            }

            logger.info(
                    "Executing CLI {} on Gradle project {} with JVM args '{}' and arguments '{}'",
                    ManifestUtils.getManifestInformation(Main.class),
                    target,
                    jvmArgs,
                    currentGradleArgs);

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Environment variables: {}",
                        envVars.keySet().stream().sorted().collect(Collectors.toList()));
            }

            if (gradleVersion.compareTo(MIN_GRADLE_VERSION_GRADLE_ISSUE_3117) < 0) {
                if (envVars.values().stream().anyMatch(s -> !s.chars().allMatch(c -> c < 128))) {
                    logger.error(
                            "Non-ASCII characters detected in environment. If build fails, try setting environment variable LC_ALL=en_US. See https://github.com/gradle/gradle/issues/3117.");
                }
            }

            build.setEnvironmentVariables(envVars);
            build.setJvmArguments(jvmArgs);
            build.withArguments(currentGradleArgs);
            build.setStandardOutput(out);
            build.setStandardError(System.err);
            build.run();
        } catch (BuildException e) {
            if (e.getCause() != null) {
                Throwable cause = e.getCause();
                // In Gradle 4.10/7.2 the loop is required as the cause contains
                //      org.gradle.internal.exceptions.LocationAwareException
                //      org.gradle.execution.TaskSelectionException
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                logger.debug("Hit https://github.com/gradle/gradle/issues/9339 ", e);
                logger.error("Build exception. Examine log for problems");
                throw new ManipulationException("Problem executing build", cause);
            } else {
                logger.error("Caught exception running build", e);
                throw new ManipulationException("Caught exception running build", e);
            }
        } catch (GradleConnectionException e) {
            // Unable to do instanceof comparison due to different classloader
            if ("org.gradle.api.UncheckedIOException".equals(e.getCause().getClass().getName())) {
                logger.debug("Hit https://github.com/gradle/gradle/issues/9339 ", e);
                logger.error("Build exception. Examine log for problems");
            } else {
                logger.error("Gradle connection exception", e);
            }
            throw new ManipulationException("Problem executing build");
        } catch (RuntimeException e) {
            logger.error("Fatal problem executing build", e);
            throw new ManipulationException("Fatal problem executing build", e);
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
        }

        connector.forProjectDirectory(target);

        if (PluginUtils.checkForSemanticBuildVersioning(logger, target)) {
            org.apache.commons.io.output.ByteArrayOutputStream stdout = new org.apache.commons.io.output.ByteArrayOutputStream();
            List<String> versionQuery = new ArrayList<>();
            versionQuery.add("--console=plain");
            versionQuery.add("printVersion");
            executeGradle(stdout, true, versionQuery);
            try {
                String projectVersion = stdout.toString(Charset.defaultCharset()).trim();

                if (StringUtils.isEmpty(projectVersion)) {
                    throw new ManipulationException(
                            "Unable to determine a project version. Has this been built from an upstream tag?");
                }
                logger.debug("Found project version {}", projectVersion);
                Files.write(
                        Paths.get(target.getPath(), "gradle.properties"),
                        (System.lineSeparator() + "version=" + projectVersion + System.lineSeparator())
                                .getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new ManipulationException("Unable to append version to gradle.properties", e);
            }

            PluginUtils.pluginRemoval(logger, target, Collections.singleton(SEMANTIC_BUILD_VERSIONING));
        }

        if (configuration.addLenientLockMode()) {
            PluginUtils.addLenientLockMode(logger, target);
        }

        GroovyUtils.runCustomGroovyScript(logger, InvocationStage.FIRST, target, configuration, null, null);

        File lockFile = new File(target, "versions.lock");
        if (lockFile.exists()) {
            logger.info("Found gradle-consistent-versions lock file {}", lockFile);
            try {
                new PrintWriter(lockFile).close();
            } catch (IOException e) {
                throw new ManipulationException("Unable to handle versions.lock", e);
            }
        }

        PluginUtils.pluginRemoval(logger, target, configuration.pluginRemoval());

        if (configuration.disableGME()) {
            logger.info("Gradle Manipulator disabled");
        } else {
            logger.debug("Executing Gradle");
            executeGradle(System.out, false, gradleArgs);
        }

        return null;
    }

    private static class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {

        private Exception exception;

        /**
         * Handles an {@code Exception} that occurred while executing the {@code Runnable} or
         * {@code Callable} command and returns an exit code suitable for returning from execute.
         *
         * @param ex the Exception thrown by the {@code Runnable}, {@code Callable} or {@code Method} user object of the
         *        command
         * @param commandLine the CommandLine representing the command or subcommand where the exception occurred
         * @param parseResult the result of parsing the command line arguments
         * @return an exit code
         */
        @Override
        public int handleExecutionException(
                Exception ex,
                CommandLine commandLine,
                CommandLine.ParseResult parseResult) {
            this.exception = ex;
            return 1;
        }

        public boolean hasException() {
            return exception != null;
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
