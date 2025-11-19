package org.jboss.pnc.gradlemanipulator.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class ManifestVerificationFunctionalTest {
    private static final Logger logger = GMLogger.getLogger(ManifestVerificationFunctionalTest.class);
    private static final String ARTIFACT_VERSION = "4.1.0.temporary-redhat-00001";
    private static final String ARTIFACT_NAME = "mongodb-driver-core-" + ARTIFACT_VERSION;
    private static final Path PATH_IN_REPOSITORY = Paths.get("org/mongodb/mongodb-driver-core/" + ARTIFACT_VERSION);

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Test
    public void verifyThriftManifest() throws IOException, URISyntaxException {
        // XXX: Caused by: org.gradle.api.plugins.UnknownPluginException: Plugin [id: 'maven'] was not found in any of
        // XXX: the following sources
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("thrift");
        TestUtils.copyDirectory("thrift", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("uploadArchives", "--stacktrace", "--info")
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":uploadArchives")).getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        File manifestFile = new File(projectRoot, "build/tmp/jar/MANIFEST.MF");
        assertTrue(manifestFile.exists());

        assertThat(FileUtils.readFileToString(manifestFile, Charset.defaultCharset()).trim()).contains(
                "Manifest-Version: 1.0\r\n" +
                        "Implementation-Version: 0.13.0.temporary-redhat-00001\r\n" +
                        "Bundle-ManifestVersion: 2\r\n" +
                        "Bundle-SymbolicName: org.apache.thrift\r\n" +
                        "Bundle-Name: Apache Thrift\r\n" +
                        "Bundle-Version: 0.13.0.temporary-redhat-00001\r\n" +
                        "Bundle-Description: Apache Thrift library\r\n" +
                        "Bundle-License: http://www.apache.org/licenses/LICENSE-2.0.txt\r\n" +
                        "Bundle-ActivationPolicy: lazy\r\n" +
                        "Export-Package: org.apache.thrift.async;uses:=\"org.apache.thrift.protoco\r\n" +
                        " l,org.apache.thrift.transport,org.slf4j,org.apache.thrift\";version=\"0.1\r\n" +
                        " 3.0.temporary-redhat-00001\",org.apache.thrift.protocol;uses:=\"org.apach\r\n" +
                        " e.thrift.transport,org.apache.thrift,org.apache.thrift.scheme\";version=\r\n" +
                        " \"0.13.0.temporary-redhat-00001\",org.apache.thrift.server;uses:=\"org.apa\r\n" +
                        " che.thrift.transport,org.apache.thrift.protocol,org.apache.thrift,org.s\r\n" +
                        " lf4j,javax.servlet,javax.servlet.http\";version=\"0.13.0.temporary-redhat\r\n" +
                        " -00001\",org.apache.thrift.transport;uses:=\"org.apache.thrift.protocol,o\r\n" +
                        " rg.apache.thrift,org.apache.http.client,org.apache.http.params,org.apac\r\n" +
                        " he.http.entity,org.apache.http.client.methods,org.apache.http,org.slf4j\r\n" +
                        " ,javax.net.ssl,javax.net,javax.security.sasl,javax.security.auth.callba\r\n" +
                        " ck\";version=\"0.13.0.temporary-redhat-00001\",org.apache.thrift;uses:=\"or\r\n" +
                        " g.apache.thrift.protocol,org.apache.thrift.async,org.apache.thrift.serv\r\n" +
                        " er,org.apache.thrift.transport,org.slf4j,org.apache.log4j,org.apache.th\r\n" +
                        " rift.scheme\";version=\"0.13.0.temporary-redhat-00001\",org.apache.thrift.\r\n" +
                        " meta_data;uses:=\"org.apache.thrift\";version=\"0.13.0.temporary-redhat-00\r\n" +
                        " 001\",org.apache.thrift.scheme;uses:=\"org.apache.thrift.protocol,org.apa\r\n" +
                        " che.thrift\";version=\"0.13.0.temporary-redhat-00001\"\r\n" +
                        "Import-Package: javax.net,javax.net.ssl,javax.security.auth.callback,jav\r\n" +
                        " ax.security.sasl,javax.servlet;resolution:=optional,javax.servlet.http;\r\n" +
                        " resolution:=optional,org.slf4j;resolution:=optional;version=\"[1.4,2)\",o\r\n" +
                        " rg.apache.http.client;resolution:=optional,org.apache.http.params;resol\r\n" +
                        " ution:=optional,org.apache.http.entity;resolution:=optional,org.apache.\r\n" +
                        " http.client.methods;resolution:=optional,org.apache.http;resolution:=op\r\n" +
                        " tional\r\n" +
                        "Specification-Version: 0.13.0.temporary-redhat-00001\r\n" +
                        "Implementation-Title: libthrift\r\n" +
                        "Specification-Title: libthrift\r\n" +
                        "Specification-Vendor: org.apache.thrift\r\n" +
                        "Implementation-Vendor: org.apache.thrift\r\n" +
                        "Implementation-Vendor-Id: org.apache.thrift");

        Path pathToArtifacts = publishDirectory.toPath()
                .resolve(
                        Paths.get("org/apache/thrift/libthrift/0.13.0.temporary-redhat-00001/"));
        final String ARTIFACT_NAME = "libthrift-0.13.0.temporary-redhat-00001";
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom")).exists();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar")).exists();

        assertThat(systemOutRule.getLinesNormalized())
                .contains(
                        "For project libthrift, task jar, updating Export-Package version 0.13.0-SNAPSHOT to 0.13.0.temporary-redhat-00001 (old version 0.13.0-SNAPSHOT)");
        assertTrue(systemOutRule.getLinesNormalized().contains("Found signing plugin; disabling"));
    }

    @Test
    public void verifyReactiveManifest() throws IOException, URISyntaxException {
        // XXX: A problem occurred evaluating root project 'reactive-streams'.
        // XXX: > Plugin with id 'osgi' not found.
        // XXX: See <https://docs.gradle.org/current/userguide/upgrading_version_5.html#the_osgi_plugin_has_been_removed>
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("6.0")) < 0);

        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("reactive-streams-jvm");
        TestUtils.copyDirectory("reactive-streams-jvm", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("uploadArchives", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":reactive-streams:uploadArchives")).getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        File manifestFile = new File(projectRoot, "api/build/tmp/jar/MANIFEST.MF");
        assertTrue(manifestFile.exists());

        try (JarInputStream jarInputStream = new JarInputStream(
                Files.newInputStream(
                        new File(
                                projectRoot,
                                "api/build/libs/reactive-streams-1.0.3.temporary-redhat-00001.jar").toPath()))) {
            List<String> lines = jarInputStream.getManifest()
                    .getMainAttributes()
                    .entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
            String stringLines = String.join("\n", lines);

            assertThat(stringLines)
                    .contains(
                            ""
                                    + "Bundle-Description: Reactive Streams API\n"
                                    + "Bundle-DocURL: http://reactive-streams.org\n"
                                    + "Bundle-ManifestVersion: 2\n"
                                    + "Bundle-Name: reactive-streams\n"
                                    + "Bundle-SymbolicName: org.reactivestreams.reactive-streams\n"
                                    + "Bundle-Vendor: Reactive Streams SIG\n"
                                    + "Bundle-Version: 1.0.3.temporary-redhat-00001\n")
                    .contains(
                            ""
                                    + "Export-Package: org.reactivestreams;version=\"1.0.3.temporary-redhat-00001\"\n"
                                    + "Implementation-Title: reactive-streams\n"
                                    + "Implementation-Vendor-Id: org.reactivestreams\n"
                                    + "Implementation-Vendor: org.reactivestreams\n"
                                    + "Implementation-Version: 1.0.3.temporary-redhat-00001\n"
                                    + "Manifest-Version: 1.0\n"
                                    + "Require-Capability: osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.6))\"\n"
                                    + "Specification-Title: reactive-streams\n"
                                    + "Specification-Vendor: org.reactivestreams\n"
                                    + "Specification-Version: 1.0.3.temporary-redhat-00001\n");
        }
    }

    @Test
    public void verifyMongoManifest() throws IOException, URISyntaxException {
        // XXX: A problem occurred configuring project ':driver-core'.
        // XXX: > Configuration <compile> not found.
        // XXX: See <https://github.com/mfuerstenau/gradle-buildconfig-plugin/issues/30>
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("mongo-java-driver");
        TestUtils.copyDirectory("mongo-java-driver", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("assemble", "publish", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":bson:publish")).getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        Path pathToArtifacts = publishDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains(
                                "Updating publication artifactId (driver-core) as it is not consistent with archivesBaseName (mongodb-driver-core)"));
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom")).exists();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar")).exists();

        File manifestFile = new File(projectRoot, "bson/build/tmp/jar/MANIFEST.MF");
        assertTrue(manifestFile.exists());

        try (JarInputStream jarInputStream = new JarInputStream(
                Files.newInputStream(
                        new File(projectRoot, "bson/build/libs/bson-" + ARTIFACT_VERSION + ".jar").toPath()))) {
            List<String> lines = jarInputStream.getManifest()
                    .getMainAttributes()
                    .entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
            String stringLines = String.join("\n", lines);

            assertThat(stringLines).contains(
                    "Export-Package: org.bson;version=\"4.1.0\"\n"
                            + "Implementation-Title: bson\n"
                            + "Implementation-Vendor-Id: org.mongodb\n"
                            + "Implementation-Vendor: org.mongodb\n"
                            + "Implementation-Version: " + ARTIFACT_VERSION + "\n"
                            + "Manifest-Version: 1.0\n"
                            + "Require-Capability: osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"\n"
                            + "Specification-Title: bson\n" + "Specification-Vendor: org.mongodb\n"
                            + "Specification-Version: " + ARTIFACT_VERSION);
        }
    }

    @Test
    public void verifyMongoManifestNoOverwrite() throws IOException, URISyntaxException {
        // XXX: A problem occurred configuring project ':driver-core'.
        // XXX: > Configuration <compile> not found.
        // XXX: See <https://github.com/mfuerstenau/gradle-buildconfig-plugin/issues/30>
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("mongo-java-driver-no-overwrite");
        TestUtils.copyDirectory("mongo-java-driver-no-overwrite", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("assemble", "publish", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":driver-core:publish")).getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        final Path pathToArtifacts = publishDirectory.toPath().resolve(PATH_IN_REPOSITORY);
        assertThat(systemOutRule.getLinesNormalized()).contains(
                "Updating publication artifactId (driver-core) as it is not consistent with archivesBaseName (mongodb-driver-core)");

        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom")).exists();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar")).exists();

        final File manifestFile = new File(projectRoot, "driver-core/build/tmp/jar/MANIFEST.MF");
        assertThat(manifestFile).exists();

        if (logger.isInfoEnabled()) {
            logger.info(
                    "Contents of manifest file {}:{}{}",
                    projectRoot.toPath().relativize(manifestFile.toPath()),
                    System.lineSeparator(),
                    FileUtils.readFileToString(manifestFile, StandardCharsets.UTF_8));
        }

        try (JarInputStream jarInputStream = new JarInputStream(
                Files.newInputStream(
                        Paths.get(
                                projectRoot.getAbsolutePath(),
                                "driver-core/build/libs/mongodb-driver-core-" + ARTIFACT_VERSION + ".jar")))) {
            List<String> lines = jarInputStream.getManifest()
                    .getMainAttributes()
                    .entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
            assertThat(lines).containsSequence(
                    "Export-Package: com.mongodb.internal.build;version=\"4.1.0\"",
                    "Implementation-Title: driver-core",
                    "Implementation-Vendor-Id: org.mongodb",
                    "Implementation-Vendor: org.mongodb",
                    "Implementation-Version: " + ARTIFACT_VERSION,
                    "Manifest-Version: 1.0",
                    "Require-Capability: osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"",
                    "Specification-Title: driver-core",
                    "Specification-Vendor: org.mongodb",
                    "Specification-Version: " + ARTIFACT_VERSION);
        }

        assertThat(systemOutRule.getLinesNormalized())
                .contains(
                        "For project driver-core, task jar, not overriding value Implementation-Version since version ("
                                + ARTIFACT_VERSION + ") has not changed")
                .contains(
                        "For project driver-core, task jar, not overriding value Specification-Version since version ("
                                + ARTIFACT_VERSION + ") has not changed");
    }

    @Test
    public void verifyASMManifest() throws IOException, URISyntaxException {
        // XXX: java-platform plugin required.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.2.1")) > 0);

        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("asm");
        TestUtils.copyDirectory("asm", projectRoot);
        new File(projectRoot, "asm").mkdirs();
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("assemble", "publish", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":asm:publish")).getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        Path pathToArtifacts = publishDirectory.toPath()
                .resolve("org/ow2/asm/asm/9.5.0.redhat-00001");
        assertThat(pathToArtifacts.resolve("asm-9.5.0.redhat-00001.pom")).exists();
        assertThat(pathToArtifacts.resolve("asm-9.5.0.redhat-00001.jar")).exists();

        File manifestFile = new File(projectRoot, "asm/build/tmp/jar/MANIFEST.MF");
        assertTrue(manifestFile.exists());

        try (JarInputStream jarInputStream = new JarInputStream(
                Files.newInputStream(new File(projectRoot, "asm/build/libs/asm-9.5.0.redhat-00001.jar").toPath()))) {
            List<String> lines = jarInputStream.getManifest()
                    .getMainAttributes()
                    .entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
            String stringLines = String.join("\n", lines);

            assertThat(stringLines).contains(
                    "Export-Package: org.objectweb.asm;version=\"9.5.0.redhat-00001\",org.objectweb.asm.signature;version=\"9.5.0.redhat-00001\"\n"
                            + "Implementation-Title: ASM, a very small and fast Java bytecode manipulation framework\n"
                            + "Implementation-Vendor-Id: org.ow2.asm\n"
                            + "Implementation-Vendor: org.ow2.asm\n"
                            + "Implementation-Version: 9.5.0.redhat-00001\n");
            assertThat(systemOutRule.getLinesNormalized())
                    .contains(
                            "For project asm, task jar, not updating Export-Package since version (9.5.0.redhat-00001) has not changed");
        }
    }
}
