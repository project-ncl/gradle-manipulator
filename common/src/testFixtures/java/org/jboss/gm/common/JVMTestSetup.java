package org.jboss.gm.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import kong.unirest.Unirest;

import org.apache.commons.lang.SystemUtils;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

import static org.assertj.core.api.Assertions.assertThat;

public class JVMTestSetup {
    public static final Path GRADLE_JDK_HOME = Paths.get(System.getProperty("user.home"), ".gradle", "jdks");

    public static final String JDK8_BASEDIR = "jdk8u272-b10";

    public static final Path JDK8_DIR = GRADLE_JDK_HOME.resolve(JDK8_BASEDIR);

    public static final Path JDK8_BIN_DIR = JDK8_DIR.resolve("bin");

    /**
     * This method will, on Linux, download and cache if it doesn't exist a JDK8 installation from AdoptOpenJDK.
     *
     * This location ($HOME/.gradle/jdks) was chosen to match https://docs.gradle.org/current/userguide/toolchains.html.
     * It utilises the same directory structure and location thereby matching potential prior downloads.
     */
    public static void setupJVM() throws IOException {
        String filename = null;
        UnArchiver ua = null;

        if (SystemUtils.IS_OS_LINUX) {
            filename = "OpenJDK8U-jdk_x64_linux_hotspot_8u272b10.tar.gz";
            ua = new TarGZipUnArchiver();
        } else if (SystemUtils.IS_OS_WINDOWS) {
            filename = "OpenJDK8U-jdk_x64_windows_hotspot_8u272b10.zip";
            ua = new ZipUnArchiver();
        } else {
            throw new ManipulationUncheckedException("Unknown OS");
        }

        if (!Files.exists(JDK8_BIN_DIR)) {
            Files.createDirectories(JDK8_DIR);

            Path destFile = GRADLE_JDK_HOME.resolve(filename);

            destFile.toFile().deleteOnExit();

            Unirest.get(
                    "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u272-b10/" + filename)
                    .asFile(destFile.toString());

            ((LogEnabled) ua).enableLogging(new ConsoleLoggerManager().getLoggerForComponent("plexus-archiver"));
            ua.setSourceFile(destFile.toFile());
            ua.setDestDirectory(GRADLE_JDK_HOME.toFile());

            assertThat(destFile).isRegularFile();
            assertThat(GRADLE_JDK_HOME).isDirectory();

            ua.extract();
        }

        assertThat(JDK8_BIN_DIR).isDirectory();
    }
}
