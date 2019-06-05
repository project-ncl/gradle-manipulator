package org.jboss.gm.analyzer.alignment;

import static org.gradle.api.Project.DEFAULT_VERSION;
import static org.jboss.gm.common.io.ManipulationIO.writeManipulationModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.jboss.gm.common.ProjectVersionFactory;
import org.jboss.gm.common.model.ManipulationModel;
import org.slf4j.Logger;

/**
 * The actual Gradle task that creates the {@code manipulation.json} file for the whole project
 * (whether it's a single or multi module project)
 */
public class AlignmentTask extends DefaultTask {

    static final String LOAD_GME = "buildscript { apply from: \"gme.gradle\" }";
    static final String GME = "gme.gradle";
    static final String LOAD_GME_END = "apply from: \"gme.gradle\"";
    static final String GME_END = "gme-end.gradle";
    static final String NAME = "generateAlignmentMetadata";

    private final Logger logger = getLogger();

    @TaskAction
    public void perform() {
        final Project project = getProject();
        final String projectName = project.getName();

        logger.info("Starting model task for project {} with GAV {}:{}:{}", project.getDisplayName(), project.getGroup(),
                projectName, project.getVersion());

        try {
            final Collection<ProjectVersionRef> deps = getAllProjectDependencies(project);
            final ManipulationCache cache = ManipulationCache.getCache(project);
            final String currentProjectVersion = project.getVersion().toString();

            cache.addDependencies(project, deps);
            project.getRepositories().forEach(cache::addRepository);
            project.getBuildscript().getRepositories().forEach(cache::addRepository);

            if (StringUtils.isBlank(project.getGroup().toString()) ||
                    DEFAULT_VERSION.equals(project.getVersion().toString())) {

                logger.warn("Project '{}:{}:{}' is not fully defined ; skipping. ", project.getGroup(), projectName,
                        project.getVersion());
            } else {
                ProjectVersionRef current = ProjectVersionFactory.withGAV(project.getGroup().toString(), projectName,
                        currentProjectVersion);

                cache.addGAV(current);
            }

            // when the set is empty, we know that this was the last alignment task to execute.
            if (cache.removeProject(projectName)) {

                Collection<ProjectVersionRef> allDeps = cache.getDependencies().values().stream().flatMap(Collection::stream)
                        .collect(Collectors.toList());

                final AlignmentService alignmentService = AlignmentServiceFactory
                        .getAlignmentService(cache.getDependencies().keySet());
                final AlignmentService.Response alignmentResponse = alignmentService.align(
                        new AlignmentService.Request(
                                cache.getGAV().peekFirst(),
                                allDeps));

                final ManipulationModel alignmentModel = cache.getModel();
                final Map<Project, Collection<ProjectVersionRef>> projectDependencies = cache.getDependencies();
                final String newVersion = alignmentResponse.getNewProjectVersion();
                final Configuration configuration = ConfigCache.getOrCreate(Configuration.class);

                if (configuration.versionModificationEnabled()) {
                    logger.info("Updating project '{}' version to {}", projectName, newVersion);
                    alignmentModel.setVersion(newVersion);
                }

                // Iterate through all modules and set their version
                projectDependencies.forEach((key, value) -> {
                    final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(key.getPath());
                    if (configuration.versionModificationEnabled()) {
                        correspondingModule.setVersion(newVersion);
                    }
                    updateModuleDependencies(correspondingModule, value, alignmentResponse);
                });

                logger.info("Completed processing for alignment and writing {} ", cache.toString());

                writeManipulationModel(project.getRootDir(), alignmentModel);
                writeGmeMarkerFile();
                writeGmeEndMarkerFile();
                writeRepositorySettingsFile(cache.getRepositories());
            }
        } catch (ManipulationException e) {
            throw new ManipulationUncheckedException(e);
        } catch (IOException e) {
            throw new ManipulationUncheckedException("Failed to write marker file", e);
        }
    }

    private void writeGmeMarkerFile() throws IOException, ManipulationException {
        File rootDir = getProject().getRootDir();
        File gmeGradle = new File(rootDir, GME);
        File rootGradle = new File(rootDir, Project.DEFAULT_BUILD_FILE);

        // TODO: Always replace or only in certain circumstances?
        if (!gmeGradle.exists()) {
            Files.copy(getClass().getResourceAsStream("/gme.gradle"), gmeGradle.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        if (rootGradle.exists()) {

            List<String> lines = FileUtils.readLines(rootGradle, Charset.defaultCharset());
            List<String> result = new ArrayList<>();

            String first = org.jboss.gm.common.utils.FileUtils.getFirstLine(lines);

            // Check if the first non-blank line is the gme phrase, otherwise inject it.
            if (!LOAD_GME.equals(first.trim())) {
                result.add(System.lineSeparator());
                result.add(LOAD_GME);
                result.add(System.lineSeparator());
                result.addAll(lines);

                FileUtils.writeLines(rootGradle, result);
            }

        } else {
            logger.warn("Unable to find build.gradle in {} to modify.", rootDir);
        }
    }

    private void writeGmeEndMarkerFile() throws IOException {
        File rootDir = getProject().getRootDir();
        File gmeGradle = new File(rootDir, GME_END);
        File rootGradle = new File(rootDir, Project.DEFAULT_BUILD_FILE);

        // TODO: Always replace or only in certain circumstances?
        if (!gmeGradle.exists()) {
            Files.copy(getClass().getResourceAsStream("/gme-end.gradle"), gmeGradle.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        if (rootGradle.exists()) {

            String line = org.jboss.gm.common.utils.FileUtils.getLastLine(rootGradle);
            logger.debug("Read line '{}' from build.gradle", line);

            if (!line.trim().equals(LOAD_GME_END)) {
                // Haven't appended it before.
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(rootGradle, true))) {
                    // Ensure the marker is on a line by itself.
                    writer.newLine();
                    writer.write(LOAD_GME_END);
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

                if (configuration.getResolutionStrategy() instanceof DefaultResolutionStrategy) {
                    DefaultResolutionStrategy defaultResolutionStrategy = (DefaultResolutionStrategy) configuration
                            .getResolutionStrategy();

                    if (defaultResolutionStrategy.getConflictResolution() == ConflictResolution.strict) {
                        // failOnVersionConflict() sets this which causes our plugin to crash out. Reset to latest to make an attempt
                        // at continuing. As Gradle creates 'decorated' we can't use reflection to change the value back to the
                        // default. Therefore use preferProjectModules as its not eager-fail.
                        logger.warn("Detected use of conflict resolution strategy strict ; resetting to preferProjectModules.");

                        defaultResolutionStrategy.preferProjectModules();
                    }
                }

                LenientConfiguration lenient = configuration.copyRecursive().getResolvedConfiguration()
                        .getLenientConfiguration();

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
                    // skip dependencies on project modules
                    if (compareTo(dep, allProjectDependencies)) {
                        project.getLogger().debug("Skipping internal project dependency {} of configuration {}",
                                dep.toString(), configuration.getName());
                        return;
                    }
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

    private boolean compareTo(ResolvedDependency dependency, Set<ProjectDependency> projectDependencies) {
        for (ProjectDependency projectDependency : projectDependencies) {
            if (StringUtils.equals(dependency.getModuleGroup(), projectDependency.getGroup()) &&
                    StringUtils.equals(dependency.getModuleName(), projectDependency.getName()) &&
                    StringUtils.equals(dependency.getModuleVersion(), projectDependency.getVersion())) {
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

    /**
     * Writes a maven settings file containing artifact repositories used by this project.
     */
    private void writeRepositorySettingsFile(Collection<ArtifactRepository> repositories) {
        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        // trying to mimick PME behaviour here - export only if repoRemovalBackup is defined and by default place it in
        // project root
        String repositoriesFilePath = config.repositoriesFile();
        if (repositoriesFilePath != null) {
            File repositoriesFile;
            if (repositoriesFilePath.equals("settings.xml")) {
                repositoriesFile = new File(getProject().getRootDir(), repositoriesFilePath);
            } else {
                repositoriesFile = new File(config.repositoriesFile());
            }

            new RepositoryExporter(repositories).export(repositoriesFile);
        }
    }
}
