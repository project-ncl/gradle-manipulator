package org.jboss.gm.cli;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.jboss.gm.analyzer.alignment.AlignmentPlugin;
import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testGradleNotFound() throws IOException {
        Main m = new Main();
        String[] args = new String[] { "-t", tempDir.newFolder().getAbsolutePath(), "-l", UUID.randomUUID().toString() };
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

        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
    }

    @Test
    public void testInvokeGradleWithProperties() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help", "--info",
                "-Dfoobar=nothing",
                "-Dfoobar=barfoo", "-DdependencyOverride.org.jboss.slf4j:*@*=", "-DgroovyScripts=file:///tmp/fake-file" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
    }

    @Test
    public void testInvokeGroovy() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "tasks",
                "-DgroovyScripts=" + groovy.toString(),
                "-DdependencyOverride.org.jboss.slf4j:*@*=" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLog().contains("Tasks runnable from root project"));
        assertTrue(systemOutRule.getLog().contains("dependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("with JVM args '[-DdependencyOverride.org.jboss.slf4j:*@*="));
        assertFalse(systemOutRule.getLog().contains(", DdependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("groovyScripts="));
        assertTrue(systemOutRule.getLog().contains("Verification tasks"));
    }

    @Test
    public void testDisableGME() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "tasks",
                "-DgroovyScripts=" + groovy.toString(),
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-Dmanipulation.disable=true" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertFalse(systemOutRule.getLog().contains("Verification tasks"));
    }

    @Test
    public void testInvokeAlignment() throws Exception {

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
        init = init.replaceFirst("(mavenCentral[(][)])", "$1" +
                "\n        flatDir {\n        dirs '" +
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n" +
                "\n        flatDir {\n        dirs '" +
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n");
        System.out.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Properties actualVersion = new Properties();
        actualVersion.load(new FileReader(new File(root, "gradle.properties")));

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10"
        };
        m.run(args);

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        assertTrue(systemOutRule.getLog().contains("Task :generateAlignmentMetadata"));

        System.out.println("Verifying it has injected gme.gradle with version " + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
    }

    @Test
    public void testInvokeGradleWithExternal() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "--trace", "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "getGitVersion" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Process 'command 'git'' finished with exit value"));
    }

    @Test
    public void testInvokeGroovyWithDuplicateOption() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "--no-colour", "-t", projectRoot.getParentFile().getAbsolutePath(), "tasks",
                "-DgroovyScripts=" + groovy.toString(),
                "-DdependencyOverride.org.jboss.slf4j:*@*=", "--no-colour" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLog().contains("Tasks runnable from root project"));
        assertTrue(systemOutRule.getLog().contains("dependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("with JVM args '[-DdependencyOverride.org.jboss.slf4j:*@*="));
        assertFalse(systemOutRule.getLog().contains(", DdependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("groovyScripts="));
    }
}
