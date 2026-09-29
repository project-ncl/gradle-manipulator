package org.jboss.pnc.gradlemanipulator.common.utils;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileUtilsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

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

    @Test
    public void testGetLastLineWithUtf8() throws IOException {
        File file = folder.newFile("test.txt");
        String content = "first line\nsecond line\nÑoño comment — résumé\n";
        org.apache.commons.io.FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);

        assertEquals("Ñoño comment — résumé", FileUtils.getLastLine(file));
    }
}
