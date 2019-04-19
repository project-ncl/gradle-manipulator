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

public class SimpleProjectFunctionalTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException, XmlPullParserException {
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = GradleRunner.create()
                .withProjectDir(simpleProjectRoot)
                .withArguments("generatePomFileForMainPublication")
                .withDebug(true)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + "generatePomFileForMainPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        final Path generatedPomPath = simpleProjectRoot.toPath().resolve("build/publications/main/pom-default.xml");
        assertThat(generatedPomPath).isRegularFile();

        // TODO: fix version assertions when plugin is implemented

        final MavenXpp3Reader reader = new MavenXpp3Reader();
        final Model model = reader.read(new FileReader(generatedPomPath.toFile()));
        assertThat(model.getGroupId()).isEqualTo("org.acme");
        assertThat(model.getArtifactId()).isEqualTo("root");
        assertThat(model.getVersion()).isEqualTo("1.0.1");
        assertThat(model.getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        tuple("commons-lang3", "3.8.1"),
                        tuple("hibernate-core", "5.3.7.Final"),
                        tuple("undertow-core", "2.0.15.Final"));
    }
}
