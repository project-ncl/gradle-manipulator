package org.jboss.gm.cli;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.util.Properties;

import kong.unirest.Unirest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.logging.LogLevel;
import org.jboss.gm.analyzer.alignment.AlignmentPlugin;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DifferentJVMTest {

    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final String jdkTar = "OpenJDK8U-jdk_x64_linux_hotspot_8u272b10.tar";
    private static final String jdkDirectory = "jdk8u272-b10";

    private static final File gradleJDKHome = new File(
            System.getProperty("user.home") + File.separator + ".gradle" + File.separator
                    + "jdks");
    private static final File jdk8 = new File(gradleJDKHome, jdkDirectory);

    /**
     * This method will, on Linux, download and cache if it doesn't exist a JDK8 installation from AdoptOpenJDK.
     *
     * This location ($HOME/.gradle/jdks) was chosen to match https://docs.gradle.org/current/userguide/toolchains.html
     * It utilises the same directory structure and location thereby matching potential prior downloads.
     */
    @BeforeClass
    public static void setupJVM() {
        if (SystemUtils.IS_OS_LINUX && !jdk8.exists()) {
            //noinspection ResultOfMethodCallIgnored
            gradleJDKHome.mkdirs();
            File tarFile = new File(gradleJDKHome + File.separator + jdkTar);
            File compressedFile = new File(tarFile + ".gz");
            Unirest.config().enableCookieManagement(false);
            Unirest.get(
                    "https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u272-b10/OpenJDK8U-jdk_x64_linux_hotspot_8u272b10.tar.gz")
                    .asFile(compressedFile.toString());

            final TarGZipUnArchiver ua = new TarGZipUnArchiver();
            ua.enableLogging(new ConsoleLoggerManager().getLoggerForComponent("plexus-archiver"));
            ua.setSourceFile(compressedFile);
            ua.setDestDirectory(tarFile.getParentFile());
            ua.extract();

            compressedFile.deleteOnExit();
        }
    }

    @Test
    public void runWithDefaultJDK() throws Exception {
        Assume.assumeTrue(SystemUtils.IS_OS_LINUX);

        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        init = init.replaceFirst("(mavenCentral[(][)])", "$1" +
                "\n        flatDir {\n        dirs '" +
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n" +
                "\n        flatDir {\n        dirs '" +
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n");
        System.out.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Properties actualVersion = new Properties();
        actualVersion.load(new FileReader(new File(root, "gradle.properties")));

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10",
                "-Dmanipulation.disable=false",
                "--info"
        };
        m.run(args);

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        assertTrue(systemOutRule.getLog().contains("Task :generateAlignmentMetadata"));
        assertTrue(systemOutRule.getLog().matches("(?s).*Environment:.*JAVA_HOME:.*"));

        System.out.println("Verifying it has injected gme.gradle with version " + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
    }

    @Test
    public void runWithJDK8() throws Exception {
        Assume.assumeTrue(SystemUtils.IS_OS_LINUX);

        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        init = init.replaceFirst("(mavenCentral[(][)])", "$1" +
                "\n        flatDir {\n        dirs '" +
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n" +
                "\n        flatDir {\n        dirs '" +
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n");
        System.out.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Properties actualVersion = new Properties();
        actualVersion.load(new FileReader(new File(root, "gradle.properties")));

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10",
                "-Dorg.gradle.java.home=" + jdk8.toString(),
                "--info"
        };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Java home: "));
        assertTrue(systemOutRule.getLog().contains("Java home overridden to: " + jdk8.getAbsolutePath()));
        assertTrue(systemOutRule.getLog().contains("Task :generateAlignmentMetadata"));
        assertTrue(systemOutRule.getLog().matches("(?s).*Environment.*JAVA_HOME.*jdk8.*"));

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        System.out.println("Verifying it has injected gme.gradle with version " + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
    }

    @Test
    public void runWithJDK8Fails() throws Exception {
        Assume.assumeTrue(SystemUtils.IS_OS_LINUX);

        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        init = init.replaceFirst("(mavenCentral[(][)])", "$1" +
                "\n        flatDir {\n        dirs '" +
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n" +
                "\n        flatDir {\n        dirs '" +
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                "'\n        }\n");
        System.out.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "FOOgenerateAlignmentMetadataBAR",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-Dorg.gradle.java.home=" + jdk8.toString(),
                "--info"
        };
        try {
            m.run(args);
        } catch (ManipulationException e) {
            assertTrue(e.getMessage().contains("Problem executing build"));
        }

        assertTrue(systemErrRule.getLog().contains(
                "Build exception but unable to transfer message due to mix of JDK versions. Examine log for problems"));
        assertTrue(systemOutRule.getLog().contains("Java home overridden to: " + jdk8));
        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        assertTrue(systemErrRule.getLog().contains("Task 'FOOgenerateAlignmentMetadataBAR' not found in root project"));
        assertFalse(gmeGradle.exists());
    }
}
