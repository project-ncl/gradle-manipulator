package org.jboss.gm.manipulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.alignment.AlignmentModel;
import org.jboss.gm.common.alignment.AlignmentUtils;
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
        final AlignmentModel alignment = AlignmentUtils.getAlignmentModelAt(multiModuleRoot);

        final BuildResult buildResult = GradleRunner.create()
                .withProjectDir(multiModuleRoot)
                .withArguments("generatePomFileForMainPublication")
                .withDebug(true)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + "generatePomFileForMainPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        // TODO: fix version assertions when plugin is implemented
        assertRootPom(multiModuleRoot, alignment);
        assertSubproject1Pom(multiModuleRoot, alignment);
        assertSubproject2Pom(multiModuleRoot, alignment);
    }

    private void assertRootPom(File multiModuleRoot, AlignmentModel alignment) throws IOException, XmlPullParserException {
        TestUtils.getModelAndCheckGAV(multiModuleRoot, alignment, "build/publications/main/pom-default.xml");
    }

    private void assertSubproject1Pom(File multiModuleRoot, AlignmentModel alignment) throws IOException,
            XmlPullParserException {
        final Pair<Model, AlignmentModel.Module> modelAndModule = TestUtils.getModelAndCheckGAV(multiModuleRoot, alignment,
                "subproject1/build/publications/main/pom-default.xml");
        final AlignmentModel.Module module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "hibernate-core"),
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "spring-context"));
    }

    private void assertSubproject2Pom(File multiModuleRoot, AlignmentModel alignment)
            throws IOException, XmlPullParserException {
        final Pair<Model, AlignmentModel.Module> modelAndModule = TestUtils.getModelAndCheckGAV(multiModuleRoot, alignment,
                "subproject2/build/publications/main/pom-default.xml");
        final AlignmentModel.Module module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "guice", "4.2.2"),
                        TestUtils.getAlignedTuple(module, "resteasy-jaxrs"));
    }
}
