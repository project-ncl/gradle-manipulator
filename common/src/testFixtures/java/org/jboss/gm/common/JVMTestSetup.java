package org.jboss.gm.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import kong.unirest.Unirest;
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;

public class JVMTestSetup {
    public static final Path GRADLE_JDK_HOME = Paths.get(System.getProperty("user.home"), ".gradle", "jdks");

    public static final String JDK8_BASEDIR = "jdk8u272-b10";

    public static final Path JDK8_DIR = GRADLE_JDK_HOME.resolve(JDK8_BASEDIR);

    public static final Path JDK8_BIN_DIR = JDK8_DIR.resolve("bin");

    public static final String JDK17_BASEDIR = "jdk-17.0.12+7";

    public static final Path JDK17_DIR = GRADLE_JDK_HOME.resolve(JDK17_BASEDIR);

    public static final Path JDK17_BIN_DIR = JDK17_DIR.resolve("bin");

    /**
     * This method will, on Linux, download and cache if it doesn't exist JDK8 and 17 installations.
     * <br/>
     * This location ($HOME/.gradle/jdks) was chosen to match the
     * <a href="https://docs.gradle.org/current/userguide/toolchains.html">Gradle toolchain default</a>.
     * It utilises the same directory structure and location thereby matching potential prior downloads.
     */
    public static void setupJVM() throws IOException {
        String filename;
        UnArchiver ua = new TarGZipUnArchiver();

        if (!SystemUtils.IS_OS_LINUX) {
            throw new ManipulationUncheckedException("Unknown OS");
        }

        if (!Files.exists(JDK8_BIN_DIR)) {
            Files.createDirectories(JDK8_DIR);
            filename = "OpenJDK8U-jdk_x64_linux_hotspot_8u272b10.tar.gz";

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
            Files.createDirectories(JDK17_DIR);
            filename = "OpenJDK17U-jdk_x64_linux_hotspot_17.0.12_7.tar.gz";

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
