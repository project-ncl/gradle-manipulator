package org.jboss.pnc.gradlemanipulator.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;

@UtilityClass
public class JavaUtils {

    private static final JavaInfo javaInfo = Jvm.current();

    @Getter
    private static final File javaHome = javaInfo.getJavaHome();

    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("JAVA_VERSION=\"(?<major>\\d+).*\"");

    /**
     * Returns the major Java version for the JDK at the given home directory
     * by reading its {@code release} file.
     */
    public static int getMajorVersion(File jdkHome) throws ManipulationException {
        Path releaseFile = jdkHome.toPath().resolve("release");
        try {
            for (String line : Files.readAllLines(releaseFile)) {
                Matcher matcher = JAVA_VERSION_PATTERN.matcher(line);
                if (matcher.matches()) {
                    return Integer.parseInt(matcher.group("major"));
                }
            }
        } catch (IOException e) {
            throw new ManipulationException("Unable to read release file from {}", jdkHome, e);
        }
        throw new ManipulationException("Unable to determine Java version from {}", jdkHome);
    }

    /**
     * Filters out JVM arguments that are unsupported for the given Java major version.
     *
     * @return a new list containing only the supported arguments
     */
    public static List<String> filterUnsupportedJvmArgs(List<String> args, int javaMajorVersion) {
        List<String> filtered = new ArrayList<>();
        for (String arg : args) {
            if ((arg.startsWith("-XX:MaxPermSize") || arg.startsWith("-XX:PermSize")) && javaMajorVersion >= 9) {
                continue;
            }
            if (arg.equals("-XX:+UseConcMarkSweepGC") && javaMajorVersion >= 14) {
                continue;
            }
            filtered.add(arg);
        }
        return filtered;
    }

    public static boolean compareJavaHome(File newHome) throws ManipulationException {
        try {
            if (newHome.getCanonicalPath().equals(javaHome.getCanonicalPath())) {
                return true;
            }
        } catch (IOException e) {
            throw new ManipulationException("Unable to compare java home locations", e);
        }
        return false;
    }
}
