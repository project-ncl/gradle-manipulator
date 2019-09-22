package org.jboss.gm.common.utils;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.ext.common.ManipulationException;

public class FileUtils {

    /**
     * Returns the first non blank line working from the end of the file.
     *
     * @param target the file to examine.
     * @return the last line that isn't blank
     * @throws IOException if an error occurs
     */
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

    /**
     * Returns either the relative path of target to root, or the root name. Used to establish
     * whether something is derived from the root project or a submodule.
     * 
     * @param root The root project path.
     * @param target The potential (sub?)project path.
     * @return The resultant path.
     */
    public static Path relativize(Path root, Path target) {
        Path result;

        if (root.equals(target)) {
            result = root.getFileName();
        } else {
            result = root.relativize(target);
        }
        return result;
    }
}
