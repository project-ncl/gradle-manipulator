package org.jboss.gm.analyzer.alignment;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.jboss.gm.common.alignment.ManipulationUtils.getCurrentManipulationModel;
import static org.jboss.gm.common.alignment.ManipulationUtils.writeUpdatedManipulationModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.ProjectVersionFactory;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.common.alignment.Utils;
import org.slf4j.Logger;

/**
 * The actual Gradle task that creates the {@code manipulation.json} file for the whole project
 * (whether it's a single or multi module project)
 */
public class AlignmentTask extends DefaultTask {

    static final String LOAD_GME = "apply from: \"gme.gradle\"";
    static final String GME = "gme.gradle";
    static final String NAME = "generateAlignmentMetadata";
    static final Set<String> projectsToAlign = new HashSet<>();

    private final Logger logger = getLogger();

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

            try {
                writeMarkerFile(project.getRootDir());
            } catch (IOException e) {
                throw new RuntimeException("Exception writing marker file");
            }
        }
    }

    private void writeMarkerFile(File rootDir) throws IOException {
        File gmeGradle = new File(rootDir, GME);
        File rootGradle = new File(rootDir, Project.DEFAULT_BUILD_FILE);

        // TODO: Always replace or only in certain circumstances?
        if (!gmeGradle.exists()) {
            Files.copy(getClass().getResourceAsStream("/gme.gradle"), gmeGradle.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        if (rootGradle.exists()) {

            String line = Utils.getLastLine(rootGradle);
            logger.debug("Read line '{}' from build.gradle", line);

            if (!line.trim().equals(LOAD_GME)) {
                // Haven't appended it before.
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(rootGradle, true))) {
                    // Ensure the marker is on a line by itself.
                    writer.newLine();
                    writer.write(LOAD_GME);
                    writer.newLine();
                    writer.flush();
                }
            }
        } else {
            logger.warn("Unable to find build.gradle in {} to modify.", rootDir);
        }
    }

    private Collection<ProjectVersionRef> getAllProjectDependencies(Project project) {
        final Set<ProjectVersionRef> result = new LinkedHashSet<>();
        project.getConfigurations().all(configuration -> {
            final ResolutionResult resolutionResult = configuration.getIncoming()
                    .getResolutionResult();//force dependency resolution - this is needed later on if we encounter deps with no version
            configuration.getAllDependencies().forEach(dep -> {
                if (dep instanceof DefaultSelfResolvingDependency) {
                    logger.warn("Ignoring dependency of type {} on project {}", dep.getClass().getName(), project.getName());
                } else if (isEmpty(dep.getVersion())) {
                    // look up resolved dependency with a toString hack
                    boolean found = false;
                    final Set<? extends DependencyResult> resolvedDependencies = getAllDependenciesFromResolutionResult(
                            resolutionResult);
                    for (DependencyResult resolvedDependency : resolvedDependencies) {
                        if (!(resolvedDependency instanceof DefaultResolvedDependencyResult)) {
                            continue;
                        }

                        final String resolvedDependencyDisplayName = ((DefaultResolvedDependencyResult) resolvedDependency)
                                .getSelected()
                                .getId().getDisplayName();
                        // the display name should be something like: "org.example:somelib:1.2.3"
                        if (resolvedDependencyDisplayName.startsWith(dep.getGroup() + ":" + dep.getName()) && StringUtils
                                .countMatches(resolvedDependencyDisplayName, ":") == 2) {
                            final int i = resolvedDependencyDisplayName.lastIndexOf(":");
                            if (i != -1) {
                                result.add(ProjectVersionFactory.withGAVAndConfiguration(dep.getGroup(), dep.getName(),
                                        resolvedDependencyDisplayName.substring(i + 1),
                                        configuration.getName()));
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        logger.warn("Ignoring empty version on dependency {} on project {}", dep.toString(), project.getName());
                    }
                } else {
                    result.add(ProjectVersionFactory.withGAVAndConfiguration(dep.getGroup(), dep.getName(), dep.getVersion(),
                            configuration.getName()));
                }
            });
        });
        return result;
    }

    private Set<? extends DependencyResult> getAllDependenciesFromResolutionResult(ResolutionResult resolutionResult) {
        try {
            return resolutionResult.getAllDependencies();
        } catch (Exception e) {
            return new HashSet<>();
        }
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
