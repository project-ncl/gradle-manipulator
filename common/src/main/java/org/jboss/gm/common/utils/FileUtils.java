package org.jboss.gm.common.utils;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.input.ReversedLinesFileReader;

public class FileUtils {
    public static String getLastLine(File target) throws IOException {
        String line = "";
        try (ReversedLinesFileReader rFile = new ReversedLinesFileReader(target, Charset.defaultCharset())) {
            while (isBlank(line)) {
                line = rFile.readLine();
            }
        }
        return line;
    }
}
