package org.jboss.gm.analyzer.alignment;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.jboss.gm.common.alignment.ManipulationUtils.getCurrentManipulationModel;
import static org.jboss.gm.common.alignment.ManipulationUtils.writeUpdatedManipulationModel;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.ProjectVersionFactory;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.slf4j.Logger;

/**
 * The actual Gradle task that creates the manipulation.json file for the whole project
 * (whether it's a single or multi module project)
 */
public class AlignmentTask extends DefaultTask {

    static final String NAME = "generateAlignmentMetadata";
    static final Set<String> projectsToAlign = new HashSet<>();

    private final Logger logger = getLogger();

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
        logger.info("Starting alignment task for project {}", projectName);

        final Collection<ProjectVersionRef> deps = getAllProjectDependencies(project);
        final AlignmentService alignmentService = AlignmentServiceFactory.getAlignmentService(project);
        final String currentProjectVersion = project.getVersion().toString();
        final AlignmentService.Response alignmentResponse = alignmentService.align(
                new AlignmentService.Request(
                        ProjectVersionFactory.withGAV(project.getGroup().toString(), projectName,
                                currentProjectVersion),
                        deps));

        final ManipulationModel alignmentModel = getCurrentManipulationModel(project.getRootDir());
        final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(project.getPath());

        correspondingModule.setVersion(alignmentResponse.getNewProjectVersion());
        updateModuleDependencies(correspondingModule, deps, alignmentResponse);

        projectsToAlign.remove(projectName);
        if (projectsToAlign.isEmpty()) {
            writeUpdatedManipulationModel(project.getRootDir(), alignmentModel);
        }
    }

    private Collection<ProjectVersionRef> getAllProjectDependencies(Project project) {
        final Set<ProjectVersionRef> result = new LinkedHashSet<>();
        project.getConfigurations().all(configuration -> configuration.getAllDependencies().forEach(dep -> {
            if (dep instanceof DefaultSelfResolvingDependency) {
                logger.warn("Ignoring dependency of type {} on project {}", dep.getClass().getName(), project.getName());
            } else if (isEmpty(dep.getVersion())) {
                logger.warn("Ignoring empty version on dependency {} on project {}", dep.toString(), project.getName());
            } else {
                result.add(ProjectVersionFactory.withGAVAndConfiguration(dep.getGroup(), dep.getName(), dep.getVersion(),
                        configuration.getName()));
            }
        }));
        return result;
    }

    private void updateModuleDependencies(ManipulationModel correspondingModule,
            Collection<ProjectVersionRef> allModuleDependencies, AlignmentService.Response alignmentResponse) {

        allModuleDependencies.forEach(d -> {
            final String newDependencyVersion = alignmentResponse.getAlignedVersionOfGav(d);
            if (newDependencyVersion != null) {
                final ProjectVersionRef newVersion = ProjectVersionFactory.withNewVersion(d, newDependencyVersion);
                correspondingModule.getAlignedDependencies().put(d.toString(), newVersion);
            }
        });
    }

}
