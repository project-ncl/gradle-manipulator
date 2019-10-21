package org.jboss.gm.cli;

import java.io.IOException;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertTrue;

public class MainTest {

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
}