package org.jboss.gm.analyzer.alignment;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.gradle.api.Project.DEFAULT_VERSION;
import static org.jboss.gm.common.io.ManipulationIO.writeManipulationModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.jboss.gm.common.ProjectVersionFactory;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;
import org.slf4j.Logger;

/**
 * The actual Gradle task that creates the {@code manipulation.json} file for the whole project
 * (whether it's a single or multi module project)
 */
public class AlignmentTask extends DefaultTask {

    static final String INJECT_GME_START = "buildscript { apply from: \"gme.gradle\" }";
    static final String GME = "gme.gradle";
    static final String INJECT_GME_END = "apply from: \"gme-pluginconfigs.gradle\"";
    static final String GME_PLUGINCONFIGS = "gme-pluginconfigs.gradle";
    static final String NAME = "generateAlignmentMetadata";

    private final Logger logger = getLogger();

    @TaskAction
    public void perform() {
        final Project project = getProject();
        final String projectName = project.getName();

        logger.info("Starting model task for project {} with GAV {}:{}:{}", project.getDisplayName(), project.getGroup(),
                projectName, project.getVersion());

        try {
            final DependenciesTuple dependenciesTuple = getDependenciesTuple(project);
            final Collection<ProjectVersionRef> deps = dependenciesTuple.getAllDeps();
            final Set<ProjectRef> dynamicDeps = dependenciesTuple.getDynamicDeps();
            final Map<ProjectRef, String> dynamicDepsToKey = dependenciesTuple.getDynamicDepsToKey();
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

                logger.debug("Adding {} to cache for scanning.", current);
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
                                cache.getGAV(),
                                allDeps, dynamicDeps));

                final ManipulationModel alignmentModel = cache.getModel();
                final Map<Project, Collection<ProjectVersionRef>> projectDependencies = cache.getDependencies();
                final Configuration configuration = ConfigCache.getOrCreate(Configuration.class);
                final String newVersion = alignmentResponse.getNewProjectVersion();

                // While we've completed processing (sub)projects the current one is not going to be the root; so
                // explicitly retrieve it and set its version.
                if (configuration.versionModificationEnabled()) {
                    project.getRootProject().setVersion(newVersion);
                    logger.info("Updating project {} version to {}", project.getRootProject(), newVersion);
                    alignmentModel.setVersion(newVersion);
                }

                // Iterate through all modules and set their version
                projectDependencies.forEach((key, value) -> {
                    final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(key.getPath());
                    if (configuration.versionModificationEnabled()) {
                        logger.info("Updating sub-project {} version to {} ", correspondingModule.getName(), newVersion);
                        correspondingModule.setVersion(newVersion);
                    }
                    updateModuleDependencies(correspondingModule, value, alignmentResponse, dynamicDepsToKey);
                });

                logger.info("Completed processing for alignment and writing {} ", cache.toString());

                writeManipulationModel(project.getRootDir(), alignmentModel);
                writeGmeMarkerFile();
                writeGmeConfigMarkerFile();
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
            Files.copy(getClass().getResourceAsStream('/' + GME), gmeGradle.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        if (rootGradle.exists()) {

            List<String> lines = FileUtils.readLines(rootGradle, Charset.defaultCharset());
            List<String> result = new ArrayList<>();

            String first = org.jboss.gm.common.utils.FileUtils.getFirstLine(lines);

            // Check if the first non-blank line is the gme phrase, otherwise inject it.
            if (!INJECT_GME_START.equals(first.trim())) {
                result.add(System.lineSeparator());
                result.add(INJECT_GME_START);
                result.add(System.lineSeparator());
                result.addAll(lines);

                FileUtils.writeLines(rootGradle, result);
            }

        } else {
            logger.warn("Unable to find build.gradle in {} to modify.", rootDir);
        }
    }

    private void writeGmeConfigMarkerFile() throws IOException {
        File rootDir = getProject().getRootDir();
        File gmeGradle = new File(rootDir, GME_PLUGINCONFIGS);
        File rootGradle = new File(rootDir, Project.DEFAULT_BUILD_FILE);

        // TODO: Always replace or only in certain circumstances?
        if (!gmeGradle.exists()) {
            Files.copy(getClass().getResourceAsStream('/' + GME_PLUGINCONFIGS), gmeGradle.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        if (rootGradle.exists()) {

            String line = org.jboss.gm.common.utils.FileUtils.getLastLine(rootGradle);
            logger.debug("Read line '{}' from build.gradle", line);

            if (!line.trim().equals(INJECT_GME_END)) {
                // Haven't appended it before.
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(rootGradle, true))) {
                    // Ensure the marker is on a line by itself.
                    writer.newLine();
                    writer.write(INJECT_GME_END);
                    writer.newLine();
                    writer.flush();
                }
            }
        } else {
            logger.warn("Unable to find build.gradle in {} to modify.", rootDir);
        }
    }

    private DependenciesTuple getDependenciesTuple(Project project) {
        Configuration internalConfig = ConfigCache.getOrCreate(Configuration.class);

        final Set<ProjectVersionRef> allDeps = new LinkedHashSet<>();
        final Set<ProjectRef> dynamicDeps = new LinkedHashSet<>();
        final Map<ProjectRef, String> dynamicDepsToKey = new HashMap<>();
        project.getConfigurations().all(configuration -> {

            if (configuration.isCanBeResolved()) {

                // using getAllDependencies here instead of getDependencies because the later
                // was returning an empty array for the root project of SpringLikeLayoutFunctionalTest
                final Set<ProjectDependency> allProjectDependencies = configuration.getAllDependencies()
                        .stream()
                        .filter(d -> ProjectDependency.class.isAssignableFrom(d.getClass()))
                        .map(ProjectDependency.class::cast)
                        .collect(Collectors.toSet());

                findDynamicDependencies(configuration, dynamicDeps, dynamicDepsToKey);

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

                    if (allDeps.add(pvr)) {
                        logger.info("For configuration {}, adding dependency to scan {} ", configuration, pvr);
                    }
                });
            } else {
                // TODO: Why are certain configurations not resolvable?
                logger.debug("Unable to resolve configuration {} for project {}", configuration.getName(), project);
            }
        });

        return new DependenciesTuple(
                updateDynamicDependenciesToExistingVersions(allDeps, dynamicDeps, project),
                dynamicDeps, dynamicDepsToKey);
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
            Collection<ProjectVersionRef> allModuleDependencies, AlignmentService.Response alignmentResponse,
            Map<ProjectRef, String> dynamicDepsToKey) {

        allModuleDependencies.forEach(d -> {
            final String newDependencyVersion = alignmentResponse.getAlignedVersionOfGav(d);
            if (newDependencyVersion != null) {
                final ProjectVersionRef newVersion = ProjectVersionFactory.withNewVersion(d, newDependencyVersion);
                // we need to make sure that dynamic dependencies are stored with their original key
                // in order for the manipulation plugin to be able to look them up properly
                final String key = dynamicDepsToKey.getOrDefault(newVersion.asProjectRef(), d.toString());
                correspondingModule.getAlignedDependencies().put(key, newVersion);
            }
        });
    }

    /**
     * Writes a maven settings file containing artifact repositories used by this project.
     */
    private void writeRepositorySettingsFile(Collection<ArtifactRepository> repositories) {
        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        String repositoriesFilePath = config.repositoriesFile();
        if (!isEmpty(repositoriesFilePath)) {
            File repositoriesFile;
            if (Paths.get(repositoriesFilePath).isAbsolute()) {
                repositoriesFile = new File(config.repositoriesFile());
            } else {
                repositoriesFile = new File(getProject().getRootDir(), repositoriesFilePath);
            }

            new RepositoryExporter(repositories).export(repositoriesFile);
        } else {
            getProject().getLogger().info("Repository export disabled.");
        }
    }

    //TODO this needs to be improved since it is very naive
    private void findDynamicDependencies(org.gradle.api.artifacts.Configuration configuration,
            Set<ProjectRef> dynamicDependencies, Map<ProjectRef, String> dynamicDepToKey) {
        configuration.getIncoming().getDependencies().withType(ExternalDependency.class).matching(d -> {
            final String requiredVersion = d.getVersionConstraint().getRequiredVersion();
            return requiredVersion.endsWith("latest.release") || requiredVersion.endsWith("+");
        }).forEach(d -> {
            final SimpleProjectRef ref = new SimpleProjectRef(d.getGroup(), d.getName());
            dynamicDependencies.add(ref);
            dynamicDepToKey.put(ref,
                    new SimpleProjectVersionRef(ref, d.getVersionConstraint().getRequiredVersion()).toString());
        });
    }

    private Set<ProjectVersionRef> updateDynamicDependenciesToExistingVersions(Set<ProjectVersionRef> allDependencies,
            Set<ProjectRef> dynamicDependencies, Project project) {
        // If there is an existing manipulation file, also use this as potential candidates.
        if (!ManipulationIO.getManipulationFilePath(project.getRootProject().getRootDir()).toFile().exists() ||
                dynamicDependencies.isEmpty()) {
            return allDependencies;
        }

        final ManipulationModel manipulationModel = ManipulationIO.readManipulationModel(project.getRootProject().getRootDir())
                .findCorrespondingChild(project.getName());

        final Set<ProjectVersionRef> allDepsWithSetDynamicDependencyVersions = new HashSet<>(
                allDependencies.size());
        for (ProjectVersionRef dependency : allDependencies) {
            // if the dependency is a dynamic dependency we need to find the version that was
            // recorded in the manipulation output of the previous run
            boolean updatedFromDynamic = false;
            if (dynamicDependencies.contains(dependency.asProjectRef())) {
                final Collection<ProjectVersionRef> previouslyAlignedDeps = manipulationModel.getAlignedDependencies().values();

                final Optional<ProjectVersionRef> matching = previouslyAlignedDeps.stream()
                        .filter(d -> d.asProjectRef().equals(dependency.asProjectRef()))
                        .findFirst();
                if (matching.isPresent()) {
                    updatedFromDynamic = true;

                    // TODO is this usage of correct?
                    final String versionFromMatching = matching.get().getVersionString();
                    allDepsWithSetDynamicDependencyVersions.add(new SimpleProjectVersionRef(matching.get().getGroupId(),
                            matching.get().getArtifactId(), removeVersionSuffix(versionFromMatching)));
                }
            }
            if (!updatedFromDynamic) {
                allDepsWithSetDynamicDependencyVersions.add(dependency);
            }
        }

        return allDepsWithSetDynamicDependencyVersions;
    }

    // TODO improve, this is pretty naive
    // Avoid adding the suffix to the version here since it messes with proper alignment
    // If the version was aligned on a previous run, we need to make sure it can be aligned again to the
    // latest version
    private String removeVersionSuffix(String version) {
        final int index = version.indexOf("-" + ConfigCache.getOrCreate(Configuration.class).versionIncrementalSuffix());
        if (index == -1) {
            return version;
        }
        return version.substring(0, index);
    }

    static class DependenciesTuple {
        private final Collection<ProjectVersionRef> allDeps;
        private final Set<ProjectRef> dynamicDeps;
        private final Map<ProjectRef, String> dynamicDepsToKey;

        public DependenciesTuple(Collection<ProjectVersionRef> allDeps, Set<ProjectRef> dynamicDeps,
                Map<ProjectRef, String> dynamicDepsToKey) {
            this.allDeps = allDeps;
            this.dynamicDeps = dynamicDeps;
            this.dynamicDepsToKey = dynamicDepsToKey;
        }

        public Collection<ProjectVersionRef> getAllDeps() {
            return allDeps;
        }

        public Set<ProjectRef> getDynamicDeps() {
            return dynamicDeps;
        }

        public Map<ProjectRef, String> getDynamicDepsToKey() {
            return dynamicDepsToKey;
        }
    }
}
