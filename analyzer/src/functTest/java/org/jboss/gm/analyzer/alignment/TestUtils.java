package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.common.alignment.ManipulationUtils;

public final class TestUtils {

    private TestUtils() {
    }

    static ManipulationModel align(File projectRoot, String projectDirName) throws IOException, URISyntaxException {

        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(projectDirName).toURI()).toFile(), projectRoot);
        assertThat(projectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments(AlignmentTask.NAME)
                .withDebug(true)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + AlignmentTask.NAME).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        return ManipulationUtils.getManipulationModelAt(projectRoot.toPath().toFile());
    }
}
