package org.jboss.gm.common.utils;

import static org.apache.commons.lang.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.ext.common.ManipulationException;

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

    /**
     * Take a collection of lines and returns the first non blank entry.
     *
     * @param lines the collection of string lines
     * @return the first non blank entry.
     * @throws ManipulationException if it cannot find a non-blank entry.
     */
    public static String getFirstLine(List<String> lines) throws ManipulationException {
        for (String line : lines) {
            if (StringUtils.isNotBlank(line)) {
                return line;
            }
        }
        throw new ManipulationException("Unable to find a non blank line in the collection");
    }
}
