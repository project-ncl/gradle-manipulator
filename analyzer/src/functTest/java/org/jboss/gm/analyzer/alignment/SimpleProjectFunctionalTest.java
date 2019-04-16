package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleProjectFunctionalTest {

	@Rule
	public TemporaryFolder tempDir = new TemporaryFolder();

	@Test
	public void ensureAlignmentFileCreatedAndAlignmentTaskRun() throws IOException, URISyntaxException {
		final File simpleProjectRoot = tempDir.newFolder("simple-project");
		TestUtils.copyDirectory("simple-project", simpleProjectRoot);
		assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

		final BuildResult buildResult = GradleRunner.create()
				.withProjectDir(simpleProjectRoot)
				.withArguments(AlignmentTask.NAME)
//				.withDebug(true)
				.withPluginClasspath()
				.build();

		assertThat(buildResult.task(":" + AlignmentTask.NAME).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
		assertThat(buildResult.getOutput()).containsIgnoringCase("Starting alignment task");

		final Path alignmentFilePath = simpleProjectRoot.toPath().resolve("alignment.json");
		assertThat(alignmentFilePath).isRegularFile();

		final AlignmentModel alignmentModel = SerializationUtils.getObjectMapper()
				.readValue(alignmentFilePath.toFile(), AlignmentModel.class);
		assertThat(alignmentModel).isNotNull().satisfies(am -> {
			assertThat(am.getBasicInfo()).isNotNull().satisfies(b -> {
				assertThat(b.getGroup()).isEqualTo("org.acme.gradle");
				assertThat(b.getName()).isEqualTo("root");
			});
			assertThat(am.getModules()).hasSize(1).satisfies(ml -> {
				assertThat(ml.get(0)).satisfies(root -> {
					assertThat(root.getNewVersion()).contains("redhat"); //ensure the project version was updated
					assertThat(root.getName()).isEqualTo("root");
					assertThat(root.getAlignedDependencies()
							//ensure that the dependencies were updated - dummy for now
							.stream().filter(d -> "compile".equals(d.getConfiguration()))).hasSize(1);
				});
			});
		});
	}
}
