package org.jboss.gm.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.JVMTestSetup.JDK8_DIR;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Properties;
import kong.unirest.Unirest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.gradle.api.logging.LogLevel;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.AlignmentPlugin;
import org.jboss.gm.analyzer.alignment.AlignmentTask;
import org.jboss.gm.common.JVMTestSetup;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import uk.org.webcompere.systemstubs.rules.SystemErrRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DifferentJVMTest {

    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule();

    @Rule
    public final SystemPropertiesRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    /**
     * This method will, on Linux, download and cache if it doesn't exist a JDK8 installation from AdoptOpenJDK.
     *
     * This location ($HOME/.gradle/jdks) was chosen to match https://docs.gradle.org/current/userguide/toolchains.html.
     * It utilises the same directory structure and location thereby matching potential prior downloads.
     */
    @BeforeClass
    public static void setupJVM() throws IOException {
        JVMTestSetup.setupJVM();
    }

    @Before
    public void reset() {
        /*
         * Reset the configuration in case other tests use Unirest. Otherwise the MainTest which calls
         * RESTUtils (and eventually Unirest) can fail. This can also be worked around with if the following
         * is added
         * tasks.test {
         * this.setForkEvery(1)
         * }
         * to the CLI build.gradle.kts.
         */
        Unirest.config().reset();

        // Reset the daemon between tests : https://discuss.gradle.org/t/stopping-gradle-daemon-via-tooling-api/16004/2
        // Under 4.10 the daemon appears to cache Config values which corrupt the tests.
        ConnectorServices.reset();
    }

    private static String escapeBackslashes(String dir) {
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
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(
                new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        init = init.replaceFirst(
                "(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
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
        String[] args = new String[] {
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10",
                "-Dmanipulation.disable=false",
                "--info"
        };
        m.run(args);

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), AlignmentTask.GME);
        assertTrue(systemOutRule.getLinesNormalized().contains("Task :generateAlignmentMetadata"));
        assertTrue(systemOutRule.getLinesNormalized().matches("(?s).*Environment:.*JAVA_HOME=.*"));

        System.out.println(
                "Verifying it has injected " + AlignmentTask.GME + " with version "
                        + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(
                FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                        .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
    }

    @Test
    public void runWithJDK8() throws Exception {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("9.0.0")) < 0);
        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(
                new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        init = init.replaceFirst(
                "(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
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
        String[] args = new String[] {
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10",
                "-Dorg.gradle.java.home=" + escapeBackslashes(JDK8_DIR.toString()),
                "--info"
        };
        m.run(args);

        assertTrue(systemOutRule.getLinesNormalized().contains("Java home: "));
        assertTrue(systemOutRule.getLinesNormalized().contains("Java home overridden to: " + JDK8_DIR));
        assertTrue(systemOutRule.getLinesNormalized().contains("Task :generateAlignmentMetadata"));
        assertTrue(systemOutRule.getLinesNormalized().matches("(?s).*Environment:.*JAVA_HOME=.*jdk8.*"));

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), AlignmentTask.GME);
        System.out.println(
                "Verifying it has injected " + AlignmentTask.GME + " with version "
                        + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(
                FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                        .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
    }

    @Test
    public void runWithJDK8Fails() throws Exception {
        // XXX: See <https://github.com/gradle/gradle/issues/9339>
        GradleVersion v = GradleVersion.current();

        assumeTrue(v.compareTo(GradleVersion.version("9.0.0")) < 0);

        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(
                new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        init = init.replaceFirst(
                "(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
                "\n        flatDir {\n            dirs '" + dir1 +
                        "'\n        }\n" +
                        "\n        flatDir {\n            dirs '" + dir2 +
                        "'\n        }\n");
        System.err.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Main m = new Main();
        String[] args = new String[] {
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "FOOgenerateAlignmentMetadataBAR",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-Dorg.gradle.java.home=" + escapeBackslashes(JDK8_DIR.toString()),
                "--info"
        };

        try {
            m.run(args);
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Problem executing build");
        }

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), AlignmentTask.GME);
        assertThat(gmeGradle).doesNotExist();
        // XXX: Caused by: java.io.StreamCorruptedException: invalid type code: 00
        assumeTrue(v.compareTo(GradleVersion.version("5.3.1")) != 0);

        if (v.compareTo(GradleVersion.version("5.4.1")) >= 0) {
            assertThat(systemErrRule.getLinesNormalized()).contains(
                    "Task 'FOOgenerateAlignmentMetadataBAR' not found in root "
                            + "project");
        } else {
            assertThat(systemOutRule.getLinesNormalized()).contains(
                    "Task 'FOOgenerateAlignmentMetadataBAR' not found in root "
                            + "project");
        }

        assertThat(systemErrRule.getLinesNormalized()).contains("Build exception. Examine log for problems");
        assertThat(systemOutRule.getLinesNormalized()).contains("Java home overridden to: " + JDK8_DIR);
    }
}
