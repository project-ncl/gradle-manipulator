package org.jboss.gm.cli;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

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

        final File projectRoot = new File(Thread.currentThread().getContextClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
    }

    @Test
    public void testInvokeGradleWithProperties() throws Exception {

        final File projectRoot = new File(Thread.currentThread().getContextClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help", "--info",
                "-Dfoobar=nothing",
                "-Dfoobar=barfoo", "-DdependencyOverride.org.jboss.slf4j:*@*=", "-DgroovyScripts=file:///tmp/fake-file" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
    }

    @Test
    public void testInvokeGroovy() throws Exception {

        final File projectRoot = new File(Thread.currentThread().getContextClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "tasks",
                "-DgroovyScripts=" + groovy.toString(),
                "-DdependencyOverride.org.jboss.slf4j:*@*=", };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLog().contains("Tasks runnable from root project"));
        assertTrue(systemOutRule.getLog().contains("dependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("with JVM args '[-DdependencyOverride.org.jboss.slf4j:*@*="));
        assertFalse(systemOutRule.getLog().contains(", DdependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("groovyScripts="));
    }
}