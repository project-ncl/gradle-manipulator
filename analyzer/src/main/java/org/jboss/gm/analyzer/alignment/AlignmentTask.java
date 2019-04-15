package org.jboss.gm.analyzer.alignment;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import static org.jboss.gm.analyzer.alignment.AlignmentUtils.getCurrentAlignmentModel;
import static org.jboss.gm.analyzer.alignment.AlignmentUtils.writeUpdatedAlignmentModel;

public class AlignmentTask extends DefaultTask {

	static final String NAME = "generateAlignmentMetadata";

	//TODO is there a way to do dependency injection in gradle plugins?
	private AlignmentService alignmentService = new DummyAlignmentService();

	/**
	 * The idea here is for every project to read the current alignment file from disk,
	 * add the dependency alignment info for the specific project which for which the task was ran
	 * and write the updated model back to disk
	 * TODO the idea described above is probably very inefficient so we probably want to explore ways to do it better
	 */
	@TaskAction
	public void perform() {
		System.out.println("Starting alignment task");

		final Project project = getProject();
		final List<CollectedDependency> deps = getAllProjectDependencies(project);
		final AlignmentService.Response alignmentResponse = alignmentService.align(
				new AlignmentService.Request(
						new GAV.Simple(project.getGroup().toString(), project.getName(), project.getVersion().toString()),
						deps));

		final AlignmentModel alignmentModel = getCurrentAlignmentModel(project);
		final AlignmentModel.Module correspondingModule = alignmentModel.findCorrespondingModule(project.getName());
		correspondingModule.setNewVersion(alignmentResponse.getNewProjectVersion());

		updateModuleDependencies(correspondingModule, deps, alignmentResponse);
		writeUpdatedAlignmentModel(project, alignmentModel);
	}

	private List<CollectedDependency> getAllProjectDependencies(Project project) {
		final List<CollectedDependency> result = new ArrayList<>();
		project.getConfigurations().all(configuration -> {
			configuration.getAllDependencies().forEach(d -> {
				result.add(new CollectedDependency(
						d.getGroup(), d.getName(), d.getVersion(), configuration.getName()));
			});
		});
		return result;
	}

	private void updateModuleDependencies(AlignmentModel.Module correspondingModule,
			List<CollectedDependency> allModuleDependencies, AlignmentService.Response alignmentResponse) {

		final List<AlignmentModel.AlignedDependency> alignedDependencies = new ArrayList<>();
		allModuleDependencies.forEach(d -> {
			final String newDependencyVersion = alignmentResponse.getAlignedVersionOfGav(d);
			if (newDependencyVersion != null) {
				alignedDependencies.add(d.withNewVersion(newDependencyVersion));
			}
		});
		correspondingModule.setAlignedDependencies(alignedDependencies);
	}

	private static class CollectedDependency extends AlignmentModel.AlignedDependency {
		public CollectedDependency(String group, String name, String version, String configuration) {
			this.group = group;
			this.name = name;
			this.version = version;
			this.configuration = configuration;
		}
	}
}
