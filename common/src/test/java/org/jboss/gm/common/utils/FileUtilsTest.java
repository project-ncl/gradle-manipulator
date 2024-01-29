package org.jboss.gm.common.utils;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileUtilsTest {
    @Test
    public void testReadLines() {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("FOO");

        assertEquals("FOO", FileUtils.getFirstLine(lines));
    }

    @Test
    public void testReadLinesFail() {
        List<String> lines = new ArrayList<>();
        lines.add("");

        assertEquals("", FileUtils.getFirstLine(lines));
    }
}
