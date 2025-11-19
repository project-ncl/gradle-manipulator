package org.jboss.pnc.gradlemanipulator.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.maven.model.Model;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.internal.Pair;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.common.io.ManipulationIO;
import org.jboss.pnc.gradlemanipulator.common.model.ManipulationModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class SimpleProjectWithSpringDMAndMavenPluginsFunctionalTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException, XmlPullParserException {
        // XXX: Caused by: org.gradle.api.plugins.UnknownPluginException: Plugin [id: 'maven'] was not found in any of
        // XXX: the following sources
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File simpleProjectRoot = tempDir.newFolder("simple-project-with-spring-dm-and-maven-plugins");
        TestUtils.copyDirectory("simple-project-with-spring-dm-and-maven-plugins", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final ManipulationModel alignment = ManipulationIO.readManipulationModel(simpleProjectRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(simpleProjectRoot)
                .withArguments("install")
                .build();
        assertThat(buildResult.task(":install"))
                .isNotNull()
                .satisfies(t -> assertThat(t.getOutcome()).isEqualTo(TaskOutcome.SUCCESS));

        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(
                m2Directory,
                alignment,
                "org/acme/root/1.0.1-redhat-00001/root-1.0.1-redhat-00001.pom",
                true);
        final ManipulationModel module = modelAndModule.getRight();
        assertThat(systemOutRule.getLinesNormalized())
                .contains(
                        "Unable to find uploadArchives parameter in tasks [install] for Legacy Maven Plugin for project root");
        assertThat(module).isNotNull();
        assertThat(modelAndModule.getLeft())
                .isNotNull()
                .satisfies(
                        m -> assertThat(m.getDependencies())
                                .extracting("artifactId", "version")
                                .containsOnly(
                                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                                        TestUtils.getAlignedTuple(module, "hibernate-core"),
                                        Tuple.tuple("hsqldb", null), // not present in manipulation model
                                        TestUtils.getAlignedTuple(module, "undertow-core"),
                                        TestUtils.getAlignedTuple(module, "slf4j-api"),
                                        Tuple.tuple("slf4j-ext", null), // not present in manipulation model
                                        TestUtils.getAlignedTuple(module, "junit", "4.12")));
        // check that BOM is present as managed dependency
        assertThat(modelAndModule.getLeft().getDependencyManagement().getDependencies())
                .extracting("artifactId", "version", "scope", "type")
                .containsOnly(
                        Tuple.tuple("spring-boot-dependencies", "1.5.19.RELEASE", "import", "pom"),
                        Tuple.tuple("slf4j-api", "1.7.25", null, "jar"),
                        Tuple.tuple("slf4j-ext", "1.7.25", null, "jar"));
    }
}
