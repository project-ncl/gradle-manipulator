package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.maven.model.Model;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.internal.Pair;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleProjectWithSpringDMAndMavenPublishPluginsFunctionalTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException, XmlPullParserException {
        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File simpleProjectRoot = tempDir.newFolder("simple-project-with-spring-dm-and-maven-publish-plugins");
        TestUtils.copyDirectory("simple-project-with-spring-dm-and-maven-publish-plugins", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final ManipulationModel alignment = ManipulationIO.readManipulationModel(simpleProjectRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("publishToMavenLocal")
                .build();
        assertThat(buildResult.task(":publishToMavenLocal")).isNotNull()
                .satisfies(t -> assertThat(t.getOutcome())
                        .isEqualTo(TaskOutcome.SUCCESS));

        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(m2Directory, alignment,
                "org/acme/root/1.0.1-redhat-00001/root-1.0.1-redhat-00001.pom", true);
        assertThat(modelAndModule.getLeft()).isNotNull()
                .satisfies(m -> assertThat(m.getDependencies())
                        .extracting("artifactId", "version")
                        .containsOnly(
                                Tuple.tuple("commons-lang3", "3.8.1"),
                                Tuple.tuple("hibernate-core", "5.0.11.Final"),
                                Tuple.tuple("hsqldb", null),
                                Tuple.tuple("undertow-core", "1.4.25.Final"),
                                Tuple.tuple("slf4j-api", "1.7.26"),
                                Tuple.tuple("slf4j-ext", null)));
        // check that BOM is present as managed dependency
        assertThat(modelAndModule.getLeft().getDependencyManagement()).isNotNull()
                .satisfies(dm -> assertThat(dm.getDependencies())
                        .extracting("artifactId", "version", "scope", "type")
                        .containsOnly(
                                Tuple.tuple("spring-boot-dependencies", "1.5.19.RELEASE", "import", "pom"),
                                Tuple.tuple("slf4j-api", "1.7.25", null, "jar"),
                                Tuple.tuple("slf4j-ext", "1.7.25", null, "jar")));
    }
}
