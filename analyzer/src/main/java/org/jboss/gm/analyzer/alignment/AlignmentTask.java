package org.jboss.gm.analyzer.alignment;

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

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.Configuration;
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

    private final Set<String> projectsToAlign = new HashSet<>();

    private final Logger logger = getLogger();

    void addProject(String project) {
        projectsToAlign.add(project);
    }

    @TaskAction
    public void perform() {
        final Project project = getProject();
        final String projectName = project.getName();
        logger.info("Starting alignment task for project {}", projectName);

        try {
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
                writeMarkerFile(project.getRootDir());
            }
        } catch (ManipulationException e) {
            throw new ManipulationUncheckedException(e);
        } catch (IOException e) {
            throw new ManipulationUncheckedException("Failed to write marker file", e);
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
        Configuration internalConfig = ConfigCache.getOrCreate(Configuration.class);

        final Set<ProjectVersionRef> result = new LinkedHashSet<>();
        project.getConfigurations().all(configuration -> {

            if (configuration.isCanBeResolved()) {

                LenientConfiguration lenient = configuration.getResolvedConfiguration().getLenientConfiguration();

                if (lenient.getUnresolvedModuleDependencies().size() > 0) {
                    if (internalConfig.ignoreUnresolvableDependencies()) {
                        logger.warn("For configuration {}; ignoring all unresolvable dependencies: {}", configuration.getName(),
                                lenient.getUnresolvedModuleDependencies());
                    } else {
                        logger.error("For configuration {}; unable to resolve all dependencies: {}", configuration.getName(),
                                lenient.getUnresolvedModuleDependencies());
                        throw new ManipulationUncheckedException("For configuration " + configuration.getName()
                                + ", unable to resolve all project dependencies: " + lenient.getUnresolvedModuleDependencies());
                    }
                }

                lenient.getFirstLevelModuleDependencies().forEach(dep -> {
                    ProjectVersionRef pvr = ProjectVersionFactory.withGAVAndConfiguration(dep.getModuleGroup(),
                            dep.getModuleName(),
                            dep.getModuleVersion(), configuration.getName());

                    if (result.add(pvr)) {
                        logger.info("Adding dependency to scan {} ", pvr);
                    }
                });
            } else {
                // TODO: Why are certain configurations not resolvable?
                logger.warn("Unable to resolve configuration {} for project {}", configuration.getName(), project);
            }
        });

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
