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

public class SimpleProjectFunctionalTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException, XmlPullParserException {
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final AlignmentModel alignment = AlignmentUtils.getAlignmentModelAt(simpleProjectRoot);

        final BuildResult buildResult = GradleRunner.create()
                .withProjectDir(simpleProjectRoot)
                .withArguments("generatePomFileForMainPublication")
                .withDebug(true)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + "generatePomFileForMainPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        final Pair<Model, AlignmentModel.Module> modelAndModule = TestUtils.getModelAndCheckGAV(simpleProjectRoot, alignment,
                "build/publications/main/pom-default.xml");

        final AlignmentModel.Module module = modelAndModule.getRight();
        assertThat(modelAndModule.getLeft().getDependencies())
                .extracting("artifactId", "version")
                .containsOnly(
                        TestUtils.getAlignedTuple(module, "hibernate-core"),
                        TestUtils.getAlignedTuple(module, "commons-lang3", "3.8.1"),
                        TestUtils.getAlignedTuple(module, "undertow-core"));
    }
}
