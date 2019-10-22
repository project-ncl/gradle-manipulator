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

        final File projectRoot = new File(Thread.currentThread().getContextClassLoader().getResource("").getPath())
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile();

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getAbsolutePath(), "--", "help" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
    }

    @Test
    public void testInvokeGroovy() throws Exception {

        final File projectRoot = new File(Thread.currentThread().getContextClassLoader().getResource("").getPath())
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile()
                .getParentFile();

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getAbsolutePath(), "-g", groovy.toString(), "--", "tasks" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLog().contains("Tasks runnable from root project"));
    }
}