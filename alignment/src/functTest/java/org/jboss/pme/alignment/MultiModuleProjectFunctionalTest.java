package org.jboss.pme.alignment;

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
import static org.jboss.pme.alignment.TestUtils.copyDirectory;

public class MultiModuleProjectFunctionalTest {

	@Rule
	public TemporaryFolder tempDir = new TemporaryFolder();

	@Test
	public void ensureAlignmentFileCreatedAndAlignmentTaskRun() throws IOException, URISyntaxException {
		final File simpleProjectRoot = tempDir.newFolder("multi-module");
		copyDirectory("multi-module", simpleProjectRoot);
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
			assertThat(am.getModules()).hasSize(3).extracting("name").containsExactly("root", "subproject1", "subproject2");

			assertThat(am.getModules()).satisfies(ml -> {
				assertThat(ml.get(1)).satisfies(subproject1 -> {
					assertThat(subproject1.getNewVersion()).contains("redhat"); //ensure the project version was updated
					assertThat(subproject1.getAlignedDependencies()
							//ensure that the dependencies were updated - dummy for now
							.stream().filter(d -> "compile".equals(d.getConfiguration()))).hasSize(1);
				});
			});
		});
	}

}
