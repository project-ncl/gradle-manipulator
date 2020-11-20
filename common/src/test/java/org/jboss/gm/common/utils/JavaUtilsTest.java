package org.jboss.gm.common.utils;

import org.assertj.core.util.Files;
import org.commonjava.maven.ext.common.ManipulationException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JavaUtilsTest {

    @Test
    public void testJava() {
        assertTrue(JavaUtils.getJavaHome().exists());
    }

    @Test
    public void testJavaCompare() throws ManipulationException {
        assertFalse(JavaUtils.compareJavaHome(Files.temporaryFolder()));
        assertTrue(JavaUtils.compareJavaHome(JavaUtils.getJavaHome()));
    }
}
