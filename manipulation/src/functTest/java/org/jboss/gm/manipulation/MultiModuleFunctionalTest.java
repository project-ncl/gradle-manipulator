package org.jboss.gm.manipulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.internal.Pair;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;

public class MultiModuleFunctionalTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException, XmlPullParserException {
        final File multiModuleRoot = tempDir.newFolder("multi-module");
        TestUtils.copyDirectory("multi-module", multiModuleRoot);
        assertThat(multiModuleRoot.toPath().resolve("build.gradle")).exists();
        final ManipulationModel alignment = ManipulationIO.readManipulationModel(multiModuleRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(multiModuleRoot)
                .withArguments("generatePomFileForMainPublication")
                .build();

        assertThat(buildResult.task(":" + "generatePomFileForMainPublication").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);

        assertRootPom(multiModuleRoot, alignment);
        assertSubproject1Pom(multiModuleRoot, alignment);
        assertSubproject2Pom(multiModuleRoot, alignment);
    }

    private void assertRootPom(File multiModuleRoot, ManipulationModel alignment)
            throws IOException, XmlPullParserException {
        TestUtils.getModelAndCheckGAV(multiModuleRoot, alignment, "build/publications/main/pom-default.xml");
    }

    private void assertSubproject1Pom(File multiModuleRoot, ManipulationModel alignment) throws IOException,
            XmlPullParserException {
        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(
                multiModuleRoot,
                alignment,
                "subproject1/build/publications/main/pom-default.xml");
        final ManipulationModel module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "hibernate-core"),
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "spring-context"));
    }

    private void assertSubproject2Pom(File multiModuleRoot, ManipulationModel alignment)
            throws IOException, XmlPullParserException {
        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(
                multiModuleRoot,
                alignment,
                "subproject2/build/publications/main/pom-default.xml");
        final ManipulationModel module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "guice", "4.2.2"),
                        TestUtils.getAlignedTuple(module, "resteasy-jaxrs"));
    }
}
