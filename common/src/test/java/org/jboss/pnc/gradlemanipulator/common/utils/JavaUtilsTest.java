package org.jboss.pnc.gradlemanipulator.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JavaUtilsTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testJava() {
        assertTrue(JavaUtils.getJavaHome().exists());
    }

    @Test
    public void testJavaCompare() throws ManipulationException, IOException {
        assertFalse(JavaUtils.compareJavaHome(folder.newFolder()));
        assertTrue(JavaUtils.compareJavaHome(JavaUtils.getJavaHome()));
    }
}
