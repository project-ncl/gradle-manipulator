package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import lombok.experimental.UtilityClass;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang.StringUtils;
import org.slf4j.helpers.MessageFormatter;

import static org.apache.commons.lang.StringUtils.isBlank;

@UtilityClass
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
     * @return the first non blank entry or empty string if it can't find one.
     */
    public static String getFirstLine(List<String> lines) {
        for (String line : lines) {
            if (StringUtils.isNotBlank(line)) {
                return line;
            }
        }
        return "";
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

    /**
     * Wraps appending to the string builder using SLF4J style substitutions.
     *
     * @param builder the string builder
     * @param message the message (possibly with parameters)
     * @param args optional parameters
     */
    public static void append(StringBuilder builder, String message, Object... args) {
        builder.append(MessageFormatter.arrayFormat(message, args).getMessage());
        builder.append(System.lineSeparator());
    }
}
