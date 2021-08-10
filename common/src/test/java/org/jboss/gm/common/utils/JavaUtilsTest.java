package org.jboss.gm.common.utils;

import java.io.IOException;

import org.commonjava.maven.ext.common.ManipulationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
