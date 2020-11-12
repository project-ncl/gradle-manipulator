package org.jboss.gm.manipulation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class ManifestVerificationFunctionalTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void verifyThriftManifest() throws IOException, URISyntaxException {

        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("thrift");
        TestUtils.copyDirectory("thrift", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                //.withDebug(true)
                .withArguments("uploadArchives", "--stacktrace", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":uploadArchives")).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        File manifestFile = new File(projectRoot, "build/tmp/jar/MANIFEST.MF");
        assertTrue(manifestFile.exists());

        assertThat(FileUtils.readFileToString(manifestFile, Charset.defaultCharset()).trim().contains("Manifest-Version: 1.0\n"
                + "Bundle-Description: Apache Thrift library\n"
                + "Implementation-Title: libthrift\n"
                + "Bundle-License: http://www.apache.org/licenses/LICENSE-2.0.txt\n"
                + "Bundle-SymbolicName: org.apache.thrift\n"
                + "Implementation-Version: 0.13.0.temporary-redhat-00001\n"
                + "Bundle-ManifestVersion: 2\n"
                + "Bundle-ActivationPolicy: lazy\n"
                + "Specification-Vendor: org.apache.thrift\n"
                + "Specification-Title: libthrift\n"
                + "Implementation-Vendor-Id: org.apache.thrift\n"
                + "Import-Package: javax.net,javax.net.ssl,javax.security.auth.callback,j\n"
                + " avax.security.sasl,javax.servlet;resolution:=optional,javax.servlet.h\n"
                + " ttp;resolution:=optional,org.slf4j;resolution:=optional;version=\"[1.4\n"
                + " ,2)\",org.apache.http.client;resolution:=optional,org.apache.http.para\n"
                + " ms;resolution:=optional,org.apache.http.entity;resolution:=optional,o\n"
                + " rg.apache.http.client.methods;resolution:=optional,org.apache.http;re\n"
                + " solution:=optional\n"
                + "Implementation-Vendor: org.apache.thrift\n"
                + "Export-Package: org.apache.thrift.async;uses:=\"org.apache.thrift.proto\n"
                + " col,org.apache.thrift.transport,org.slf4j,org.apache.thrift\";version=\n"
                + " \"0.13.0.temporary-redhat-00001\",org.apache.thrift.protocol;uses:=\"org\n"
                + " .apache.thrift.transport,org.apache.thrift,org.apache.thrift.scheme\";\n"
                + " version=\"0.13.0.temporary-redhat-00001\",org.apache.thrift.server;uses\n"
                + " :=\"org.apache.thrift.transport,org.apache.thrift.protocol,org.apache.\n"
                + " thrift,org.slf4j,javax.servlet,javax.servlet.http\";version=\"0.13.0.te\n"
                + " mporary-redhat-00001\",org.apache.thrift.transport;uses:=\"org.apache.t\n"
                + " hrift.protocol,org.apache.thrift,org.apache.http.client,org.apache.ht\n"
                + " tp.params,org.apache.http.entity,org.apache.http.client.methods,org.a\n"
                + " pache.http,org.slf4j,javax.net.ssl,javax.net,javax.security.sasl,java\n"
                + " x.security.auth.callback\";version=\"0.13.0.temporary-redhat-00001\",org\n"
                + " .apache.thrift;uses:=\"org.apache.thrift.protocol,org.apache.thrift.as\n"
                + " ync,org.apache.thrift.server,org.apache.thrift.transport,org.slf4j,or\n"
                + " g.apache.log4j,org.apache.thrift.scheme\";version=\"0.13.0.temporary-re\n"
                + " dhat-00001\",org.apache.thrift.meta_data;uses:=\"org.apache.thrift\";ver\n"
                + " sion=\"0.13.0.temporary-redhat-00001\",org.apache.thrift.scheme;uses:=\"\n"
                + " org.apache.thrift.protocol,org.apache.thrift\";version=\"0.13.0.tempora\n"
                + " ry-redhat-00001\"\n"
                + "Bundle-Name: Apache Thrift\n"
                + "Bundle-Version: 0.13.0.temporary-redhat-00001\n"
                + "Specification-Version: 0.13.0.temporary-redhat-00001\n"));

        Path pathToArtifacts = publishDirectory.toPath().resolve(
                Paths.get("org/apache/thrift/libthrift/0.13.0.temporary-redhat-00001/"));
        final String ARTIFACT_NAME = "libthrift-0.13.0.temporary-redhat-00001";
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".pom").toFile().exists()).isTrue();
        assertThat(pathToArtifacts.resolve(ARTIFACT_NAME + ".jar").toFile().exists()).isTrue();
    }

    @Test
    public void verifyReactiveManifest() throws IOException, URISyntaxException {
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("reactive-streams-jvm");
        TestUtils.copyDirectory("reactive-streams-jvm", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withGradleVersion("5.6.4")
                //.withDebug(true)
                .withArguments("uploadArchives", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":reactive-streams:uploadArchives")).getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        File manifestFile = new File(projectRoot, "api/build/tmp/jar/MANIFEST.MF");
        assertTrue(manifestFile.exists());

        JarInputStream jarInputStream = new JarInputStream(
                new FileInputStream(new File(projectRoot, "api/build/libs/reactive-streams-1.0.3.temporary-redhat-00001.jar")));
        List<String> lines = jarInputStream.getManifest().getMainAttributes()
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        String stringLines = String.join("\n", lines);

        assertThat(stringLines)
                .contains(""
                        + "Bundle-Description: Reactive Streams API\n"
                        + "Bundle-DocURL: http://reactive-streams.org\n"
                        + "Bundle-ManifestVersion: 2\n"
                        + "Bundle-Name: reactive-streams\n"
                        + "Bundle-SymbolicName: org.reactivestreams.reactive-streams\n"
                        + "Bundle-Vendor: Reactive Streams SIG\n"
                        + "Bundle-Version: 1.0.3.temporary-redhat-00001\n");
        assertThat(stringLines)
                .contains(""
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

    @Test
    public void verifyMongoManifest() throws IOException, URISyntaxException {
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File publishDirectory = tempDir.newFolder("publishDirectory");
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final File projectRoot = tempDir.newFolder("mongo-java-driver");
        TestUtils.copyDirectory("mongo-java-driver", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                //.withDebug(true)
                .withArguments("publishToMavenLocal", "--info")
                .withPluginClasspath()
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":bson:publishToMavenLocal")).getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        File manifestFile = new File(projectRoot, "bson/build/tmp/jar/MANIFEST.MF");
        assertTrue(manifestFile.exists());

        JarInputStream jarInputStream = new JarInputStream(
                new FileInputStream(new File(projectRoot, "bson/build/libs/bson-4.1.0.temporary-redhat-00001.jar")));
        List<String> lines = jarInputStream.getManifest().getMainAttributes()
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        String stringLines = String.join("\n", lines);

        assertThat(stringLines).contains("Export-Package: org.bson;version=\"4.1.0\"\n"
                + "Implementation-Title: bson\n"
                + "Implementation-Vendor-Id: org.mongodb\n"
                + "Implementation-Vendor: org.mongodb\n"
                + "Implementation-Version: 4.1.0.temporary-redhat-00001\n"
                + "Manifest-Version: 1.0\n"
                + "Require-Capability: osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"\n"
                + "Specification-Title: bson\n" + "Specification-Vendor: org.mongodb\n"
                + "Specification-Version: 4.1.0.temporary-redhat-00001");
    }
}
