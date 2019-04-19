package org.jboss.gm.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MultiModuleFunctionalTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException, XmlPullParserException {
        final File multiModuleRoot = tempDir.newFolder("multi-module");
        TestUtils.copyDirectory("multi-module", multiModuleRoot);
        assertThat(multiModuleRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = GradleRunner.create()
                .withProjectDir(multiModuleRoot)
                .withArguments("generatePomFileForMainPublication")
                .withDebug(true)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + "generatePomFileForMainPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        // TODO: fix version assertions when plugin is implemented
        assertRootPom(multiModuleRoot);
        assertSubproject1Pom(multiModuleRoot);
        assertSubproject2Pom(multiModuleRoot);
    }

    private void assertRootPom(File multiModuleRoot) throws IOException, XmlPullParserException {
        final Path generatedPomPath = multiModuleRoot.toPath().resolve("build/publications/main/pom-default.xml");
        assertThat(generatedPomPath).isRegularFile();

        final MavenXpp3Reader reader = new MavenXpp3Reader();
        final Model model = reader.read(new FileReader(generatedPomPath.toFile()));
        assertThat(model.getGroupId()).isEqualTo("org.acme");
        assertThat(model.getArtifactId()).isEqualTo("root");
        assertThat(model.getVersion()).isEqualTo("1.1.2");
    }

    private void assertSubproject1Pom(File multiModuleRoot) throws IOException, XmlPullParserException {
        final Path generatedPomPath = multiModuleRoot.toPath().resolve("subproject1/build/publications/main/pom-default.xml");
        assertThat(generatedPomPath).isRegularFile();

        final MavenXpp3Reader reader = new MavenXpp3Reader();
        final Model model = reader.read(new FileReader(generatedPomPath.toFile()));
        assertThat(model.getGroupId()).isEqualTo("org.acme");
        assertThat(model.getArtifactId()).isEqualTo("subproject1");
        assertThat(model.getVersion()).isEqualTo("1.1.2");
        assertThat(model.getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        tuple("hibernate-core", "5.4.2.Final"),
                        tuple("commons-lang3", "3.8.1"),
                        tuple("spring-context", "5.1.6.RELEASE"));
    }

    private void assertSubproject2Pom(File multiModuleRoot) throws IOException, XmlPullParserException {
        final Path generatedPomPath = multiModuleRoot.toPath().resolve("subproject2/build/publications/main/pom-default.xml");
        assertThat(generatedPomPath).isRegularFile();

        final MavenXpp3Reader reader = new MavenXpp3Reader();
        final Model model = reader.read(new FileReader(generatedPomPath.toFile()));
        assertThat(model.getGroupId()).isEqualTo("org.acme");
        assertThat(model.getArtifactId()).isEqualTo("subproject2");
        assertThat(model.getVersion()).isEqualTo("1.1.2");
        assertThat(model.getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        tuple("commons-lang3", "3.8.1"),
                        tuple("guice", "4.2.2"),
                        tuple("resteasy-jaxrs", "3.6.3.SP1"));
    }
}
