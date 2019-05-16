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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ProjectVersionFactory;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.common.alignment.ManipulationUtils;
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

    private final Logger logger = getLogger();

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

            final ManipulationModel alignmentModel = ManipulationUtils.getCurrentManipulationModel(project.getRootDir(),
                    new AdditionalPropertiesManipulationModelCache(project));
            final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(project.getPath());

            correspondingModule.setVersion(alignmentResponse.getNewProjectVersion());
            updateModuleDependencies(correspondingModule, deps, alignmentResponse);

            final Set<String> projectsToAlign = AlignmentPlugin.getProjectsToAlign(project);
            projectsToAlign.remove(projectName);
            if (projectsToAlign.isEmpty()) { // when the set is empty, we know that this was the last alignment task to execute
                makeProjectVersionConsistent(alignmentModel);
                writeUpdatedManipulationModel(project.getRootDir(), alignmentModel);
                writeMarkerFile(project.getRootDir());
            }
        } catch (ManipulationException e) {
            throw new ManipulationUncheckedException(e);
        } catch (IOException e) {
            throw new ManipulationUncheckedException("Failed to write marker file", e);
        }
    }

    private void makeProjectVersionConsistent(ManipulationModel alignmentModel) {
        updateVersion(alignmentModel, getVersionToUse(alignmentModel));
    }

    private void updateVersion(ManipulationModel alignmentModel, String versionToUse) {
        alignmentModel.setVersion(versionToUse);
        final Map<String, ManipulationModel> children = alignmentModel.getChildren();
        for (ManipulationModel child : children.values()) {
            updateVersion(child, versionToUse);
        }
    }

    // in order to make sure that all modules use the same version suffix
    // we just use the maximum version we encounter
    private String getVersionToUse(ManipulationModel alignmentModel) {
        String versionToUse = alignmentModel.getVersion();
        for (ManipulationModel child : alignmentModel.getChildren().values()) {
            final String childVersionToUse = getVersionToUse(child);
            if (childVersionToUse.compareTo(versionToUse) > 0) { //comparing the string here yields the proper result due to the nature of the suffix
                versionToUse = childVersionToUse;
            }
        }
        return versionToUse;
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

                // using getAllDependencies here instead of getDependencies because the later
                // was returning an empty array for the root project of SpringLikeLayoutFunctionalTest
                final Set<ProjectDependency> allProjectDependencies = configuration.getAllDependencies()
                        .stream()
                        .filter(d -> ProjectDependency.class.isAssignableFrom(d.getClass()))
                        .map(ProjectDependency.class::cast)
                        .collect(Collectors.toSet());

                LenientConfiguration lenient = configuration.getResolvedConfiguration().getLenientConfiguration();

                // We don't care about modules of the project being unresolvable at this stage. Had we not excluded them,
                // we would get false negatives
                final Set<UnresolvedDependency> unresolvedDependencies = getUnresolvedDependenciesExcludingProjectDependencies(
                        lenient, allProjectDependencies);

                if (unresolvedDependencies.size() > 0) {
                    if (internalConfig.ignoreUnresolvableDependencies()) {
                        logger.warn("For configuration {}; ignoring all unresolvable dependencies: {}", configuration.getName(),
                                unresolvedDependencies);
                    } else {
                        logger.error("For configuration {}; unable to resolve all dependencies: {}", configuration.getName(),
                                lenient.getUnresolvedModuleDependencies());
                        throw new ManipulationUncheckedException("For configuration " + configuration.getName()
                                + ", unable to resolve all project dependencies: " + unresolvedDependencies);
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

    private Set<UnresolvedDependency> getUnresolvedDependenciesExcludingProjectDependencies(LenientConfiguration lenient,
            Set<ProjectDependency> allProjectModules) {
        return lenient.getUnresolvedModuleDependencies()
                .stream()
                .filter(d -> !compareTo(d, allProjectModules))
                .collect(Collectors.toSet());
    }

    private boolean compareTo(UnresolvedDependency unresolvedDependency, Set<ProjectDependency> projectDependencies) {
        ModuleVersionSelector moduleVersionSelector = unresolvedDependency.getSelector();
        for (ProjectDependency projectDependency : projectDependencies) {
            if (StringUtils.equals(moduleVersionSelector.getGroup(), projectDependency.getGroup()) &&
                    StringUtils.equals(moduleVersionSelector.getName(), projectDependency.getName()) &&
                    StringUtils.equals(moduleVersionSelector.getVersion(), projectDependency.getVersion())) {
                return true;
            }
        }
        return false;
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
