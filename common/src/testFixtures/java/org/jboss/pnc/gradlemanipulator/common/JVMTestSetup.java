package org.jboss.pnc.gradlemanipulator.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import kong.unirest.Unirest;
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationUncheckedException;

public class JVMTestSetup {
    public static final Path GRADLE_JDK_HOME = Paths.get(System.getProperty("user.home"), ".gradle", "jdks");

    private static final String OS_NAME;
    private static final String ARCH;
    private static final String HOME_SUBDIR;

    static {
        if (SystemUtils.IS_OS_LINUX) {
            OS_NAME = "linux";
            ARCH = "x64";
            HOME_SUBDIR = "";
        } else if (SystemUtils.IS_OS_MAC) {
            OS_NAME = "mac";
            ARCH = System.getProperty("os.arch").contains("aarch64") || System.getProperty("os.arch").contains("arm64")
                    ? "aarch64"
                    : "x64";
            HOME_SUBDIR = "Contents/Home";
        } else {
            throw new ManipulationUncheckedException("Unsupported OS: " + System.getProperty("os.name"));
        }
    }

    // JDK 8 aarch64 macOS builds don't exist; use x64 via Rosetta
    private static final String JDK8_ARCH = SystemUtils.IS_OS_MAC ? "x64" : ARCH;

    public static final String JDK8_BASEDIR = "jdk8u272-b10";

    private static final Path JDK8_EXTRACT_DIR = GRADLE_JDK_HOME.resolve(JDK8_BASEDIR);

    public static final Path JDK8_DIR = HOME_SUBDIR.isEmpty() ? JDK8_EXTRACT_DIR
            : JDK8_EXTRACT_DIR.resolve(HOME_SUBDIR);

    public static final Path JDK8_BIN_DIR = JDK8_DIR.resolve("bin");

    public static final String JDK17_BASEDIR = "jdk-17.0.12+7";

    private static final Path JDK17_EXTRACT_DIR = GRADLE_JDK_HOME.resolve(JDK17_BASEDIR);

    public static final Path JDK17_DIR = HOME_SUBDIR.isEmpty() ? JDK17_EXTRACT_DIR
            : JDK17_EXTRACT_DIR.resolve(HOME_SUBDIR);

    public static final Path JDK17_BIN_DIR = JDK17_DIR.resolve("bin");

    /**
     * This method will download and cache if it doesn't exist JDK8 and 17 installations.
     * <br/>
     * This location ($HOME/.gradle/jdks) was chosen to match the
     * <a href="https://docs.gradle.org/current/userguide/toolchains.html">Gradle toolchain default</a>.
     * It utilises the same directory structure and location thereby matching potential prior downloads.
     */
    public static void setupJVM() throws IOException {
        String filename;
        UnArchiver ua = new TarGZipUnArchiver();

        if (!Files.exists(JDK8_BIN_DIR)) {
            Files.createDirectories(JDK8_EXTRACT_DIR);
            filename = "OpenJDK8U-jdk_" + JDK8_ARCH + "_" + OS_NAME + "_hotspot_8u272b10.tar.gz";

            Path destFile = GRADLE_JDK_HOME.resolve(filename);

            destFile.toFile().deleteOnExit();

            Unirest.get(
                    "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u272-b10/" + filename)
                    .asFile(destFile.toString());

            ua.setSourceFile(destFile.toFile());
            ua.setDestDirectory(GRADLE_JDK_HOME.toFile());

            assertThat(destFile).isRegularFile();
            assertThat(GRADLE_JDK_HOME).isDirectory();

            ua.extract();
        }
        if (!Files.exists(JDK17_BIN_DIR)) {
            Files.createDirectories(JDK17_EXTRACT_DIR);
            filename = "OpenJDK17U-jdk_" + ARCH + "_" + OS_NAME + "_hotspot_17.0.12_7.tar.gz";

            Path destFile = GRADLE_JDK_HOME.resolve(filename);

            destFile.toFile().deleteOnExit();

            Unirest.get(
                    "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/" + filename)
                    .asFile(destFile.toString());

            ua.setSourceFile(destFile.toFile());
            ua.setDestDirectory(GRADLE_JDK_HOME.toFile());

            assertThat(destFile).isRegularFile();
            assertThat(GRADLE_JDK_HOME).isDirectory();

            ua.extract();
        }

        assertThat(JDK8_BIN_DIR).isDirectory();
        assertThat(JDK17_BIN_DIR).isDirectory();
    }
}
