package org.jboss.gm.cli;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import kong.unirest.Unirest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.SystemUtils;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.gradle.api.logging.LogLevel;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.AlignmentPlugin;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DifferentJVMTest {

    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final Path GRADLE_JDK_HOME = Paths.get(System.getProperty("user.home"), ".gradle", "jdks");

    private static final String JDK8_BASEDIR = "jdk8u272-b10";

    private static final Path JDK8_DIR = GRADLE_JDK_HOME.resolve(JDK8_BASEDIR);

    private static final Path JDK8_BIN_DIR = JDK8_DIR.resolve("bin");

    /**
     * This method will, on Linux, download and cache if it doesn't exist a JDK8 installation from AdoptOpenJDK.
     *
     * This location ($HOME/.gradle/jdks) was chosen to match https://docs.gradle.org/current/userguide/toolchains.html.
     * It utilises the same directory structure and location thereby matching potential prior downloads.
     */
    @BeforeClass
    public static void setupJVM() throws IOException {
        String filename = null;
        UnArchiver ua = null;

        if (SystemUtils.IS_OS_LINUX) {
            filename = "OpenJDK8U-jdk_x64_linux_hotspot_8u272b10.tar.gz";
            ua = new TarGZipUnArchiver();
        } else if (SystemUtils.IS_OS_WINDOWS) {
            filename = "OpenJDK8U-jdk_x64_windows_hotspot_8u272b10.zip";
            ua = new ZipUnArchiver();
        } else {
            assumeTrue(filename != null && ua != null);
        }

        if (!Files.exists(JDK8_BIN_DIR)) {
            Files.createDirectories(JDK8_DIR);

            Path destFile = GRADLE_JDK_HOME.resolve(filename);

            destFile.toFile().deleteOnExit();

            Unirest.get(
                    "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u272-b10/" + filename)
                    .asFile(destFile.toString());

            ((LogEnabled) ua).enableLogging(new ConsoleLoggerManager().getLoggerForComponent("plexus-archiver"));
            ua.setSourceFile(destFile.toFile());
            ua.setDestDirectory(GRADLE_JDK_HOME.toFile());

            assertThat(destFile).isRegularFile();
            assertThat(GRADLE_JDK_HOME).isDirectory();

            ua.extract();

            /*
             * Reset the configuration in case other tests use Unirest. Otherwise the MainTest which calls
             * RESTUtils (and eventually Unirest) can fail. This can also be worked around with if the following
             * is added
             * tasks.test {
             *  this.setForkEvery(1)
             * }
             * to the CLI build.gradle.kts.
             */
            Unirest.config().reset();
        }

        assertThat(JDK8_BIN_DIR).isDirectory();
    }

    @Before
    public void reset() {
        // Reset the daemon between tests : https://discuss.gradle.org/t/stopping-gradle-daemon-via-tooling-api/16004/2
        // Under 4.10 the daemon appears to cache Config values which corrupt the tests.
        ConnectorServices.reset();
    }

    private String escapeBackslashes(String dir) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return FilenameUtils.normalize(dir, true).replace("/", "\\\\\\\\");
        }

        return dir;
    }

    @Test
    public void runWithDefaultJDK() throws Exception {
        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        init = init.replaceFirst("(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
                "\n        flatDir {\n            dirs '" + dir1 +
                        "'\n        }\n" +
                        "\n        flatDir {\n            dirs '" + dir2 +
                        "'\n        }\n");
        System.err.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Properties actualVersion = new Properties();

        try (Reader reader = new FileReader(new File(root, "gradle.properties"))) {
            actualVersion.load(reader);
        }

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10",
                "-Dmanipulation.disable=false",
                "--info"
        };
        m.run(args);

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        assertTrue(systemOutRule.getLog().contains("Task :generateAlignmentMetadata"));
        assertTrue(systemOutRule.getLog().matches("(?s).*Environment:.*JAVA_HOME:.*"));

        System.out.println("Verifying it has injected gme.gradle with version " + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
    }

    @Test
    public void runWithJDK8() throws Exception {
        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        init = init.replaceFirst("(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
                "\n        flatDir {\n            dirs '" + dir1 +
                        "'\n        }\n" +
                        "\n        flatDir {\n            dirs '" + dir2 +
                        "'\n        }\n");
        System.err.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Properties actualVersion = new Properties();

        try (Reader reader = new FileReader(new File(root, "gradle.properties"))) {
            actualVersion.load(reader);
        }

        System.out.println("JDK8 is: " + JDK8_DIR);

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10",
                "-Dorg.gradle.java.home=" + escapeBackslashes(JDK8_DIR.toString()),
                "--info"
        };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Java home: "));
        assertTrue(systemOutRule.getLog().contains("Java home overridden to: " + JDK8_DIR));
        assertTrue(systemOutRule.getLog().contains("Task :generateAlignmentMetadata"));
        assertTrue(systemOutRule.getLog().matches("(?s).*Environment.*JAVA_HOME.*jdk8.*"));

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        System.out.println("Verifying it has injected gme.gradle with version " + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
    }

    @Test
    public void runWithJDK8Fails() throws Exception {
        // XXX: See <https://github.com/gradle/gradle/issues/9339>
        GradleVersion v = GradleVersion.current();
        assumeTrue(v.compareTo(GradleVersion.version("5.0")) < 0 || v.compareTo(GradleVersion.version("5.3.1")) >= 0);

        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        init = init.replaceFirst("(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
                "\n        flatDir {\n            dirs '" + dir1 +
                        "'\n        }\n" +
                        "\n        flatDir {\n            dirs '" + dir2 +
                        "'\n        }\n");
        System.err.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "FOOgenerateAlignmentMetadataBAR",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-Dorg.gradle.java.home=" + escapeBackslashes(JDK8_DIR.toString()),
                "--info"
        };

        try {
            m.run(args);
        } catch (Exception e) {
            if (v.compareTo(GradleVersion.version("5.0")) >= 0) {
                // XXX: Ignored for 4.10 as Exception differs between Gradle versions and System.err is checked later anyway
                assertTrue(e.getMessage().contains("Problem executing build"));
            }
        }

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        assertFalse(gmeGradle.exists());
        // XXX: Caused by: java.io.StreamCorruptedException: invalid type code: 00
        assumeTrue(v.compareTo(GradleVersion.version("5.3.1")) != 0);
        assertTrue(systemErrRule.getLog().contains("Task 'FOOgenerateAlignmentMetadataBAR' not found in root project"));
        // XXX: Gradle 4.x doesn't trigger the same Exception
        assumeTrue(v.compareTo(GradleVersion.version("5.0")) >= 0);
        assertTrue(systemErrRule.getLog().contains(
                "Build exception but unable to transfer message due to mix of JDK versions. Examine log for problems"));
        assertTrue(systemOutRule.getLog().contains("Java home overridden to: " + JDK8_DIR));
    }
}
