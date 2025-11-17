package org.jboss.pnc.gradlemanipulator.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ch.qos.logback.core.pattern.color.ANSIConstants;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;
import org.aeonbits.owner.ConfigCache;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.gradle.api.logging.LogLevel;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.AlignmentPlugin;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.AlignmentTask;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.model.ManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.rules.LoggingRule;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;
import uk.org.webcompere.systemstubs.rules.SystemErrRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class MainTest {

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

    @Rule
    public final EnvironmentVariablesRule environmentVariables = new EnvironmentVariablesRule();

    private static String escapeBackslashes(String dir) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return FilenameUtils.normalize(dir, true).replace("/", "\\\\\\\\");
        }

        return dir;
    }

    @Before
    public void reset() {
        // Reset the daemon between tests : https://discuss.gradle.org/t/stopping-gradle-daemon-via-tooling-api/16004/2
        // Under 4.10 the daemon appears to cache Config values which corrupt the tests.
        ConnectorServices.reset();
        // Spurious caching issues so clear the cache for each test
        ConfigCache.clear();
    }

    @Test
    public void testGradleNotFound() throws IOException {
        Main m = new Main();
        String[] args = new String[] {
                "-t",
                tempDir.newFolder().getAbsolutePath(),
                "-l",
                UUID.randomUUID().toString() };
        try {
            m.run(args);
            fail("No exception thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Unable to locate Gradle installation"));
        }
    }

    @Test
    public void testInvokeGradle() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help" };
        m.run(args);

        assertFalse(systemOutRule.getLinesNormalized().contains("loggingColours=false"));
        assertTrue(systemOutRule.getLinesNormalized().contains("Welcome to Gradle"));
        assertTrue(systemOutRule.getLinesNormalized().contains(ANSIConstants.ESC_START));
    }

    @Test
    public void testInvokeGradleDisabledColor() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        environmentVariables.set("NO_COLOR", "1");

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help" };
        m.run(args);

        assertTrue(systemOutRule.getLinesNormalized().contains("loggingColours=false"));
        assertTrue(systemOutRule.getLinesNormalized().contains("Welcome to Gradle"));
        assertFalse(systemOutRule.getLinesNormalized().contains(ANSIConstants.ESC_START));
    }

    @Test
    public void testInvokeGradleWithProperties() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] {
                "-d",
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "help",
                "--info",
                "-Dfoobar=nothing",
                "-Dfoobar=barfoo",
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-DgroovyScripts=https://www.foo.com/tmp/fake-file" };
        m.run(args);

        assertTrue(systemOutRule.getLinesNormalized().contains("Welcome to Gradle"));
        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains(
                                "with JVM args '[-Dfoobar=barfoo, -DdependencyOverride.org.jboss.slf4j:*@*=, -DgroovyScripts=https://www.foo.com/tmp/fake-file,"));
    }

    @Test
    public void testInvokeGradleWithEnvironmentVars() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] {
                "-d",
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "help",
                "--info",
                "-eAA_JAVA_RUNTIME_HOME=/tmp/fake" };
        m.run(args);

        assertTrue(systemOutRule.getLinesNormalized().contains("Welcome to Gradle"));
        assertTrue(
                systemOutRule.getLinesNormalized().contains("Environment variables: [AA_JAVA_RUNTIME_HOME"));
    }

    @Test
    public void testInvokeGroovy() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] {
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "tasks",
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-DgroovyScripts=" + groovy };
        m.run(args);

        int start = systemOutRule.getLinesNormalized().indexOf("JVM arguments: [") + 16;
        int end = systemOutRule.getLinesNormalized().indexOf(",", start);
        String jvmArgs = systemOutRule.getLinesNormalized().substring(start, end);
        assertTrue(StringUtils.indexOf(systemOutRule.getLinesNormalized(), jvmArgs, end) > 0);
        assertTrue(systemOutRule.getLinesNormalized().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLinesNormalized().contains("dependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains("with JVM args '[-DdependencyOverride.org.jboss.slf4j:*@*="));
        assertFalse(systemOutRule.getLinesNormalized().contains(", DdependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLinesNormalized().contains("groovyScripts="));
        assertTrue(systemOutRule.getLinesNormalized().contains("Verification tasks"));
        assertTrue(systemOutRule.getLinesNormalized().contains("Executor org.zeroturnaround.exec.ProcessExecutor"));
    }

    @Test
    public void testDisableGME() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] {
                "-d",
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "tasks",
                "-DgroovyScripts=" + groovy,
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-Dmanipulation.disable=true" };
        m.run(args);

        assertTrue(systemOutRule.getLinesNormalized().contains("Running Groovy script on"));
        assertFalse(systemOutRule.getLinesNormalized().contains("Verification tasks"));
        assertTrue(systemOutRule.getLinesNormalized().contains("Gradle Manipulator disabled"));
    }

    @Test
    public void testInvokeAlignment() throws Exception {
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

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");
        Main m = new Main();
        String[] args = new String[] {
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10",
                "-DgroovyScripts=" + groovy
        };

        int result = m.run(args);
        assertEquals(0, result);

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), AlignmentTask.GME);
        assertTrue(systemOutRule.getLinesNormalized().contains("Task :generateAlignmentMetadata"));

        System.err.println(
                "Verifying it has injected " + AlignmentTask.GME + " with version "
                        + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(
                FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                        .contains(
                                "org.jboss.pnc.gradle-manipulator:manipulation:"
                                        + actualVersion.getProperty("version")));
        assertTrue(systemOutRule.getLinesNormalized().contains(ANSIConstants.ESC_START));
        assertTrue(systemOutRule.getLinesNormalized().contains("Executor org.zeroturnaround.exec.ProcessExecutor"));
    }

    @Test
    public void testInvokeAlignmentFails() throws Exception {
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

        environmentVariables.set("NO_COLOR", "1");

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .getParent());
        String init = FileUtils.readFileToString(
                new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
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
                "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10"
        };

        try {
            m.run(args);
        } catch (Exception e) {
            assertThat(e.getCause().getMessage()).contains(
                    "must be configured in order for dependency scanning to "
                            + "work");
        }

        assertThat(systemErrRule.getLinesNormalized()).doesNotContain(ANSIConstants.ESC_START);

        if (GradleVersion.current().compareTo(GradleVersion.version("5.4.1")) >= 0) {
            assertThat(systemErrRule.getLinesNormalized()).contains(
                    "'" + Configuration.DA + "' must be configured in order for dependency scanning to work");
        } else {
            assertThat(systemOutRule.getLinesNormalized()).contains(
                    "'" + Configuration.DA + "' must be configured in order for dependency scanning to work");
        }
    }

    @Test
    public void testInvokeGradleWithExternal() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] {
                "--trace",
                "-d",
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "getGitVersion" };
        m.run(args);

        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains("Process 'command '/usr/bin/git'' finished with exit value"));
    }

    @Test
    public void testInvokeGroovyWithDuplicateOption() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] {
                "--no-colour",
                "-t",
                projectRoot.getParentFile().getAbsolutePath(),
                "tasks",
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-DgroovyScripts=" + groovy };
        m.run(args);

        assertTrue(systemOutRule.getLinesNormalized().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLinesNormalized().contains("dependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains("with JVM args '[-DdependencyOverride.org.jboss.slf4j:*@*="));
        assertFalse(systemOutRule.getLinesNormalized().contains(", DdependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLinesNormalized().contains("groovyScripts="));
    }

    @Test
    public void testGradleInvalidTarget1() throws IOException {
        Main m = new Main();
        String[] args = new String[] { "-t", tempDir.newFile().toString() };
        try {
            m.run(args);
            fail("No exception thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Pass project root as directory not file"));
        }
    }

    @Test
    public void testGradleInvalidTarget2() {
        Main m = new Main();
        String[] args = new String[] { "-t", UUID.randomUUID().toString() };
        try {
            m.run(args);
            fail("No exception thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Unable to locate target directory"));
        }
    }

    @Test
    public void testSetEnvironmentVariables() throws Exception {
        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        environmentVariables.set("LETTERS", "ÀàÈèÌìÒòÙù").set("JAVA_OPTS", "-Xmx260m");

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help" };
        m.run(args);

        assertThat(systemOutRule.getLinesNormalized()).contains("LETTERS");
        // Should be the last in the JVM arg list.
        assertThat(systemOutRule.getLinesNormalized()).contains(" -Xmx260m]");
    }

    @Test
    public void testWipeLock() throws Exception {
        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().toString(), "help" };
        m.run(args);
        assertTrue(systemOutRule.getLinesNormalized().contains("Found gradle-consistent-versions lock file"));
        File lockFile = new File(projectRoot.getParentFile(), "versions.lock");
        assertEquals(0, lockFile.length());
    }

    @Test
    public void testInvokeBrokenGradle() throws Exception {
        final File projectRoot = tempDir.newFolder("broken-gradle-1");
        FileUtils.copyDirectory(
                Paths
                        .get(MainTest.class.getClassLoader().getResource("broken-gradle").toURI())
                        .toFile(),
                projectRoot);

        Main m = new Main();
        String[] args = new String[] {
                "-t",
                projectRoot.getAbsolutePath(),
                "help",
                "-DignoreUnresolvableDependencies=true",
        };

        try {
            m.run(args);
            fail("No exception thrown");
        } catch (ManipulationException e) {
            assertTrue(e.getMessage().contains("Gradle 8.10.1 is a known broken version"));
        }
    }

    @Test
    public void testFixWithGroovyBrokenGradle() throws Exception {
        final File projectRoot = tempDir.newFolder("broken-gradle-2");
        FileUtils.copyDirectory(
                Paths
                        .get(MainTest.class.getClassLoader().getResource("broken-gradle").toURI())
                        .toFile(),
                projectRoot);
        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("fixGradle810.groovy");

        Main m = new Main();
        String[] args = new String[] {
                "-t",
                projectRoot.getAbsolutePath(),
                "help",
                "-DgroovyScripts=" + groovy
        };

        int result = m.run(args);
        assertEquals(0, result);

        // Verify gradle-daemon-jvm.properties is removed
        assertThat(systemOutRule.getLinesNormalized()).contains("Removing gradle/gradle-daemon-jvm.properties");
    }
}
