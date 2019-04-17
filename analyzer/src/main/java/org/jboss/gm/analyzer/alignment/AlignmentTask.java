package org.jboss.gm.analyzer.alignment;

import static org.jboss.gm.analyzer.alignment.AlignmentUtils.getCurrentAlignmentModel;
import static org.jboss.gm.analyzer.alignment.AlignmentUtils.writeUpdatedAlignmentModel;

import java.util.ArrayList;
import java.util.List;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

public class AlignmentTask extends DefaultTask {

    static final String NAME = "generateAlignmentMetadata";

    /**
     * The idea here is for every project to read the current alignment file from disk,
     * add the dependency alignment info for the specific project which for which the task was ran
     * and write the updated model back to disk
     * TODO the idea described above is probably very inefficient so we probably want to explore ways to do it better
     */
    @TaskAction
    public void perform() {
        final Project project = getProject();
        final String projectName = project.getName();
        System.out.println("Starting alignment task for project " + projectName);

        final List<ProjectVersionRef> deps = getAllProjectDependencies(project);
        final AlignmentService alignmentService = AlignmentServiceFactory.getAlignmentService(project);
        final AlignmentService.Response alignmentResponse = alignmentService.align(
                new AlignmentService.Request(
                        AlignmentUtils.withGAV(project.getGroup().toString(), projectName, project.getVersion().toString()),
                        deps));

        final AlignmentModel alignmentModel = getCurrentAlignmentModel(project);
        final AlignmentModel.Module correspondingModule = alignmentModel.findCorrespondingModule(projectName);
        correspondingModule.setNewVersion(alignmentResponse.getNewProjectVersion());

        updateModuleDependencies(correspondingModule, deps, alignmentResponse);
        writeUpdatedAlignmentModel(project, alignmentModel);
    }

    private List<ProjectVersionRef> getAllProjectDependencies(Project project) {
        final List<ProjectVersionRef> result = new ArrayList<>();
        project.getConfigurations().all(configuration -> configuration.getAllDependencies().forEach(d -> result.add(
                AlignmentUtils.withGAVAndConfiguration(d.getGroup(), d.getName(), d.getVersion(), configuration.getName()))));
        return result;
    }

    private void updateModuleDependencies(AlignmentModel.Module correspondingModule,
            List<ProjectVersionRef> allModuleDependencies, AlignmentService.Response alignmentResponse) {

        final List<ProjectVersionRef> alignedDependencies = new ArrayList<>();
        allModuleDependencies.forEach(d -> {
            final String newDependencyVersion = alignmentResponse.getAlignedVersionOfGav(d);
            if (newDependencyVersion != null) {
                alignedDependencies.add(AlignmentUtils.withNewVersion(d, newDependencyVersion));
            }
        });
        correspondingModule.setAlignedDependencies(alignedDependencies);
    }

}
