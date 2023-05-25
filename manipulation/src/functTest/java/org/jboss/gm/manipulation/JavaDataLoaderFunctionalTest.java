package org.jboss.gm.manipulation;

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
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class JavaDataLoaderFunctionalTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void verifyPublicationPomIsOverridden() throws IOException, URISyntaxException, XmlPullParserException {
        final File projectRoot = tempDir.newFolder("java-dataloader-like-project");
        TestUtils.copyDirectory("java-dataloader-like-project", projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final ManipulationModel alignment = ManipulationIO.readManipulationModel(projectRoot);

        final BuildResult buildResult = TestUtils.createGradleRunner()
                .withProjectDir(projectRoot)
                .withArguments("--info", "generatePomFileForMavenPublication")
                .build();

        assertThat(buildResult.task(":" + "generatePomFileForMavenPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        final Pair<Model, ManipulationModel> modelAndModule = TestUtils.getModelAndCheckGAV(projectRoot, alignment,
                "build/publications/maven/pom-default.xml");
        assertThat(systemOutRule.getLog())
                .contains(
                        "Unable to find publish parameter in tasks [generatePomFileForMavenPublication] for Maven Publish Plugin for project java-dataloader-like-project");
        assertEquals("1.2.0.redhat-00001", modelAndModule.right.getVersion());
    }
}
