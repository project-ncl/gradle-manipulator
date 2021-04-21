package org.jboss.gm.analyzer.alignment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.beanutils.ContextClassLoaderLocal;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.common.json.GAV;
import org.commonjava.maven.ext.common.json.ModulesItem;
import org.commonjava.maven.ext.common.json.PME;
import org.commonjava.maven.ext.common.util.JSONUtils;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.analyzer.alignment.AlignmentService.Response;
import org.jboss.gm.analyzer.alignment.io.LockFileIO;
import org.jboss.gm.analyzer.alignment.io.RepositoryExporter;
import org.jboss.gm.analyzer.alignment.io.SettingsFileIO;
import org.jboss.gm.analyzer.alignment.util.Comparator;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.GroovyUtils;
import org.jboss.gm.common.utils.ProjectUtils;
import org.jboss.gm.common.versioning.DynamicVersionParser;
import org.jboss.gm.common.versioning.ProjectVersionFactory;
import org.jboss.gm.common.versioning.RelaxedProjectVersionRef;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.gradle.api.Project.DEFAULT_VERSION;
import static org.jboss.gm.common.io.ManipulationIO.writeManipulationModel;
import static org.jboss.gm.common.utils.FileUtils.append;

/**
 * The actual Gradle task that creates the {@code manipulation.json} file for the whole project
 * (whether it's a single or multi module project)
 */
public class AlignmentTask extends DefaultTask {

    public static final String INJECT_GME_START = "buildscript { apply from: \"gme.gradle\" }";
    public static final String INJECT_GME_START_KOTLIN = "buildscript { project.apply { from(\"gme.gradle\") } }";
    public static final String INJECT_GME_END = "apply from: \"gme-pluginconfigs.gradle\"";
    public static final String INJECT_GME_END_KOTLIN = "project.apply { from(\"gme-pluginconfigs.gradle\") }";
    public static final String GME = "gme.gradle";
    public static final String GRADLE = "gradle";
    public static final String GME_REPOS = "gme-repos.gradle";
    public static final String APPLY_GME_REPOS = "buildscript { apply from: new File(buildscript.getSourceFile().getParentFile(),\"gme-repos.gradle\"), to: buildscript }";
    public static final String GME_PLUGINCONFIGS = "gme-pluginconfigs.gradle";
    public static final String NAME = "generateAlignmentMetadata";

    private static final ContextClassLoaderLocal<AtomicBoolean> configOutput = new ContextClassLoaderLocal<AtomicBoolean>() {
        @Override
        protected AtomicBoolean initialValue() {
            return new AtomicBoolean();
        }
    };

    private final Logger logger = GMLogger.getLogger(getClass());

    @TaskAction
    public void perform() {
        final Project project = getProject();
        final Configuration configuration = ConfigCache.getOrCreate(Configuration.class);
        final ManipulationCache cache = ManipulationCache.getCache(project);
        final Path root = project.getRootDir().toPath();
        final ManipulationModel alignmentModel = cache.getModel();

        String groupId = ProjectUtils.getRealGroupId(project);
        String projectName = project.getName();

        // Only output the config once to avoid noisy logging.
        if (logger.isInfoEnabled() && !configOutput.get().getAndSet(true)) {
            logger.info("Configuration now has properties {}", configuration.dumpCurrentConfig());
        }
        final String currentProjectVersion = project.getVersion().toString();
        logger.info("Starting alignment task for project in directory '{}' with GAV {}:{}:{}",
                project.getProjectDir().getName(), groupId, projectName, currentProjectVersion);

        // If processing the root project _and_ we have a Maven publication configured then verify artifactId / groupId.
        Project rootProject = project.getRootProject();
        if (project.equals(rootProject)) {
            logger.debug("Processing root project in directory {}", root);
            PublishingExtension extension = rootProject.getExtensions().findByType(PublishingExtension.class);
            Map<String, MavenPublication> publications = (extension == null ? Collections.emptyMap()
                    : extension
                            .getPublications()
                            .withType(MavenPublication.class)
                            .getAsMap());
            if (publications.size() > 1) {
                logger.error("Multiple publications for a single project. Found {}", publications);
            }
            for (MavenPublication p : publications.values()) {
                if (!rootProject.getGroup().equals(p.getGroupId())) {
                    logger.warn("Mismatched groupId between project {} and publication {} ; resetting to publication.",
                            rootProject.getGroup(),
                            p.getGroupId());
                    groupId = p.getGroupId();
                    rootProject.setGroup(p.getGroupId());
                    alignmentModel.setGroup(p.getGroupId());
                }
                if (!rootProject.getName().equals(p.getArtifactId())) {
                    logger.warn("Mismatched artifactId between project {} and publication {} ; resetting to publication.",
                            rootProject.getName(),
                            p.getArtifactId());
                    projectName = p.getArtifactId();
                    ProjectUtils.updateNameField(rootProject, p.getArtifactId());
                    alignmentModel.setName(p.getArtifactId());
                }
            }
        }

        try {
            final Set<ProjectVersionRef> lockFileDeps = LockFileIO
                    .allProjectVersionRefsFromLockfiles(LockFileIO.getLocksRootPath(project));
            final Map<RelaxedProjectVersionRef, ProjectVersionRef> dependencies = processAnyExistingManipulationFile(
                    project,
                    getDependencies(project, configuration, lockFileDeps));

            logger.debug("For project {} adding to the cache the dependencies {}", project, dependencies); // TODO: Trace level?
            cache.addDependencies(project, dependencies);

            project.getRepositories().forEach(r -> cache.addRepository(r,
                    org.jboss.gm.common.utils.FileUtils.relativize(root, project.getProjectDir().toPath())));
            project.getBuildscript().getRepositories().forEach(r -> cache.addRepository(r,
                    org.jboss.gm.common.utils.FileUtils.relativize(root, project.getProjectDir().toPath())));

            if (StringUtils.isBlank(groupId) ||
                    DEFAULT_VERSION.equals(currentProjectVersion)) {
                logger.warn("Project '{}:{}:{}' is not fully defined ; skipping. ", groupId, projectName,
                        currentProjectVersion);
            } else {
                ProjectVersionRef current = ProjectVersionFactory.withGAV(groupId, projectName,
                        currentProjectVersion);

                logger.debug("Adding {} to cache for scanning.", current);
                cache.addGAV(project, current);
            }

            // when the set is empty, we know that this was the last alignment task to execute.
            if (cache.removeProject(project)) {
                logger.info("Completed scanning {} projects; now processing for exclusions/REST/overrides...",
                        cache.getDependencies().size());
                Set<ProjectVersionRef> allDeps = cache.getDependencies().values().stream()
                        .flatMap(m -> m.values().stream()).collect(Collectors.toSet());

                final AlignmentService alignmentService = AlignmentServiceFactory
                        .getAlignmentService(configuration, cache.getDependencies().keySet());

                final Response alignmentResponse = alignmentService.align(
                        new AlignmentService.Request(cache.getProjectVersionRefs(configuration.versionSuffixSnapshot()),
                                allDeps));

                final Map<Project, Map<RelaxedProjectVersionRef, ProjectVersionRef>> projectDependencies = cache
                        .getDependencies();
                final String newVersion = alignmentResponse.getNewProjectVersion();

                // While we've completed processing (sub)projects the current one is not going to be the root; so
                // explicitly retrieve it and set its version.
                if (configuration.versionModificationEnabled()) {
                    logger.info("Updating model version for {} from {} to {}", rootProject,
                            rootProject.getVersion(), newVersion);
                    alignmentModel.setVersion(newVersion);
                }
                // Even if version modification is disabled, set the original version for consistency in the JSON file.
                final Optional<Project> originalVersion = rootProject.getAllprojects()
                        .stream()
                        .filter(p -> !DEFAULT_VERSION.equals(
                                p.getVersion().toString()))
                        .findAny();
                if (originalVersion.isPresent()) {
                    alignmentModel.setOriginalVersion(originalVersion.get().getVersion().toString());
                } else {
                    throw new ManipulationUncheckedException("Unable to locate a suitable original version");
                }

                final Set<ProjectVersionRef> nonAligned = new HashSet<>();
                // Iterate through all modules and set their version
                projectDependencies.forEach((key, value) -> {
                    final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(key);
                    if (configuration.versionModificationEnabled()) {
                        logger.info("Updating sub-project {} (path: {}) from version {} to {} ",
                                correspondingModule, correspondingModule.getProjectPathName(), value, newVersion);
                        correspondingModule.setVersion(newVersion);
                    }
                    updateModuleDynamicDependencies(correspondingModule, value);
                    updateModuleDependencies(correspondingModule, value, alignmentResponse);
                });

                // artifactId / rootProject.getName
                final String artifactId = SettingsFileIO.writeProjectNameIfNeeded(getProject().getRootDir());
                if (!isEmpty(artifactId)) {
                    logger.debug("Located artifactId ({}) for {}::{}", artifactId, alignmentModel.getGroup(),
                            alignmentModel.getVersion());
                    alignmentModel.setName(artifactId);
                }

                // groupId
                if (isEmpty(alignmentModel.getGroup())) {
                    List<String> candidates = cache.getModel().getChildren()
                            .values()
                            .stream().map(ManipulationModel::getGroup)
                            .filter(StringUtils::isNotBlank)
                            .distinct()
                            .collect(Collectors.toList());

                    logger.debug("Found potential candidates of {} to establish a groupId.", candidates);
                    final String commonPrefix = StringUtils.stripEnd(StringUtils.getCommonPrefix(candidates
                            .toArray(new String[0])), ".");

                    if (isEmpty(commonPrefix)) {
                        throw new ManipulationException(
                                "Empty groupId but unable to determine a suitable replacement from any child modules.");
                    }

                    logger.warn("groupId for {} ({}) is empty. Defaulting to common prefix of '{}'", project.getRootProject(),
                            project.getProjectDir(), commonPrefix);
                    alignmentModel.setGroup(commonPrefix);
                }

                logger.info("Completed processing for alignment and writing {} ", cache);
                GroovyUtils.runCustomGroovyScript(logger, InvocationStage.LAST, project.getRootDir(), configuration,
                        project.getRootProject(),
                        alignmentModel);
                writeManipulationModel(project.getRootDir(), alignmentModel);
                // Ordering is important here ; we mustn't inject the gme-repos file before iterating over all *.gradle
                // files.
                updateAllExtraGradleFilesWithGmeRepos();

                logger.info("For project script is {}  and build file {} ", project.getBuildscript(), project.getBuildFile());
                logger.info("For project {} ", project.getBuildscript().getSourceFile());
                writeGmeMarkerFile(configuration, project.getRootProject().getBuildFile());
                writeGmeConfigMarkerFile(project.getRootProject().getBuildFile());
                writeGmeReposMarkerFile();
                writeRepositorySettingsFile(cache.getRepositories());
                processAlignmentReport(project, configuration, cache, nonAligned);
            } else {
                logger.debug("Still have {} projects to scan", cache.getProjectCounterRemaining());
            }

            // this needs to happen for each project, not just the last one
            LockFileIO.renameAllLockFiles(LockFileIO.getLocksRootPath(project));

        } catch (ManipulationException | IOException e) {
            throw new ManipulationUncheckedException(e);
        }
    }

    private void writeGmeMarkerFile(Configuration configuration, File rootGradle) throws IOException, ManipulationException {
        File rootDir = getProject().getRootDir();
        File gmeGradle = new File(rootDir, GME);

        if (!gmeGradle.exists()) {
            Files.copy(getClass().getResourceAsStream('/' + GME), gmeGradle.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (!isEmpty(configuration.manipulationVersion())) {
            String gmeGradleString = FileUtils.readFileToString(gmeGradle, Charset.defaultCharset());
            String currentVersion = gmeGradleString.replaceFirst(
                    "(?s).*(classpath \"org.jboss.gm:manipulation:)([0-9]+\\.[0-9]+(-SNAPSHOT)??)\".*", "$2");
            logger.info("Replacing version {} with {} for the ManipulationPlugin", currentVersion,
                    configuration.manipulationVersion());
            FileUtils.writeStringToFile(gmeGradle,
                    gmeGradleString.replaceFirst(currentVersion, configuration.manipulationVersion()),
                    Charset.defaultCharset());
        }

        if (rootGradle.exists()) {

            List<String> lines = FileUtils.readLines(rootGradle, Charset.defaultCharset());
            List<String> result = new ArrayList<>();

            String injectedLine = rootGradle.getName().endsWith(".kts") ? INJECT_GME_START_KOTLIN : INJECT_GME_START;
            String first = org.jboss.gm.common.utils.FileUtils.getFirstLine(lines);
            logger.debug("Read first line '{}' from {}", first, rootGradle);

            // Check if the first non-blank line is the gme phrase, otherwise inject it.
            if (!injectedLine.equals(first.trim())) {
                result.add(System.lineSeparator());
                result.add(injectedLine);
                result.add(System.lineSeparator());
                result.addAll(lines);

                FileUtils.writeLines(rootGradle, result);
            }

        } else {
            logger.warn("Unable to find build.gradle in {} to modify.", rootDir);
        }
    }

    private void writeGmeConfigMarkerFile(File rootGradle) throws IOException {
        File rootDir = getProject().getRootDir();
        File gmeGradle = new File(rootDir, GME_PLUGINCONFIGS);

        if (!gmeGradle.exists()) {
            Files.copy(getClass().getResourceAsStream('/' + GME_PLUGINCONFIGS), gmeGradle.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        if (rootGradle.exists()) {

            String injectedLine = rootGradle.getName().endsWith(".kts") ? INJECT_GME_END_KOTLIN : INJECT_GME_END;
            String line = org.jboss.gm.common.utils.FileUtils.getLastLine(rootGradle);
            logger.debug("Read last line '{}' from {}", line, rootGradle);

            if (!line.trim().equals(injectedLine)) {
                // Haven't appended it before.
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(rootGradle, true))) {
                    // Ensure the marker is on a line by itself.
                    writer.newLine();
                    writer.write(injectedLine);
                    writer.newLine();
                    writer.flush();
                }
            }
        } else {
            logger.warn("Unable to find build.gradle in {} to modify.", rootDir);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void writeGmeReposMarkerFile() throws IOException {
        File rootDir = getProject().getRootDir();
        File gradleDir = new File(rootDir, GRADLE);
        gradleDir.mkdir();
        File gmeReposGradle = new File(gradleDir, GME_REPOS);

        Files.copy(getClass().getResourceAsStream('/' + GME_REPOS), gmeReposGradle.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void updateAllExtraGradleFilesWithGmeRepos() throws IOException, ManipulationException {
        final File rootDir = getProject().getRootDir();
        final File gradleScriptsDirectory = rootDir.toPath().resolve("gradle").toFile();
        if (!gradleScriptsDirectory.exists()) {
            return;
        }
        final Collection<File> extraGradleScripts = FileUtils.listFiles(gradleScriptsDirectory, new SuffixFileFilter(".gradle"),
                DirectoryFileFilter.DIRECTORY);
        for (File extraGradleScript : extraGradleScripts) {
            final List<String> lines = FileUtils.readLines(extraGradleScript, Charset.defaultCharset());

            if (!APPLY_GME_REPOS.equals(org.jboss.gm.common.utils.FileUtils.getFirstLine(lines))) {
                final List<String> result = new ArrayList<>(lines.size() + 2);
                result.add(APPLY_GME_REPOS);
                result.add(System.lineSeparator());
                result.addAll(lines);
                FileUtils.writeLines(extraGradleScript, result);
            }
        }
    }

    private Map<RelaxedProjectVersionRef, ProjectVersionRef> getDependencies(Project project, Configuration internalConfig,
            Set<ProjectVersionRef> lockFileDeps) {

        final Map<RelaxedProjectVersionRef, ProjectVersionRef> depMap = new HashMap<>();
        project.getConfigurations().all(configuration -> {
            if (configuration.isCanBeResolved()) {

                logger.debug("Examining configuration {}", configuration.getName());

                // using getAllDependencies here instead of getDependencies because the later
                // was returning an empty array for the root project of SpringLikeLayoutFunctionalTest
                final DependencySet allDependencies = configuration.getAllDependencies();
                final Set<ProjectDependency> allProjectDependencies = allDependencies
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

                // If we have dependency constraints we can get a ClassCastException when attempting to copy the configurations.
                // This is due to an unchecked cast in
                // org.gradle.api.internal.artifacts.configurations.DefaultConfiguration::createCopy { ...
                // copiedDependencyConstraints.add(((DefaultDependencyConstraint) dependencyConstraint).copy());
                // ... }
                // When our constraint is a DefaultProjectDependencyConstraint this is a problem. Therefore, as we normally
                // need to copy the configurations to ensure we resolve all dependencies (See
                // analyzer/src/functTest/java/org/jboss/gm/analyzer/alignment/DynamicWithLocksProjectFunctionalTest.java for
                // an example) first verify if DefaultProjectDependencyConstraint occurs in the list of constraints.
                LenientConfiguration lenient;
                if (configuration
                        .getAllDependencyConstraints().stream()
                        .noneMatch(d -> d instanceof DefaultProjectDependencyConstraint)) {
                    lenient = configuration.copyRecursive().getResolvedConfiguration().getLenientConfiguration();
                } else {
                    logger.warn("DefaultProjectDependencyConstraint found ({}), not copying configuration",
                            configuration.getAllDependencyConstraints());
                    lenient = configuration.getResolvedConfiguration().getLenientConfiguration();
                }

                // We don't care about modules of the project being unresolvable at this stage. Had we not excluded them,
                // we would get false negatives
                final Set<UnresolvedDependency> unresolvedDependencies = getUnresolvedDependenciesExcludingProjectDependencies(
                        lenient, allProjectDependencies);

                if (!unresolvedDependencies.isEmpty()) {
                    if (internalConfig.ignoreUnresolvableDependencies()) {
                        logger.warn("For configuration {}; ignoring all unresolvable dependencies: {}",
                                configuration.getName(),
                                unresolvedDependencies);
                    } else {

                        logger.error("For configuration {}; unable to resolve all dependencies: {}",
                                configuration.getName(),
                                lenient.getUnresolvedModuleDependencies());
                        for (UnresolvedDependency ud : unresolvedDependencies) {
                            logger.error("Unresolved had problem in {} with ", ud.getSelector(), ud.getProblem());
                        }
                        throw new ManipulationUncheckedException(
                                "For configuration {}, unable to resolve all project dependencies: {}",
                                configuration.getName(), unresolvedDependencies);
                    }
                }
                Set<ResolvedDependency> target;
                if (internalConfig.overrideTransitive() == null || !internalConfig.overrideTransitive()) {
                    if (internalConfig.overrideTransitive() == null &&
                            project.getPluginManager().hasPlugin("com.github.johnrengelman.shadow")) {
                        // Check for shadow jar configuration.
                        throw new ManipulationUncheckedException(
                                "Shadow plugin (for shading) configured but overrideTransitive has not been explicitly enabled or disabled.");
                    }
                    target = lenient.getFirstLevelModuleDependencies();
                } else {
                    target = lenient.getAllModuleDependencies();
                    logger.debug("Returning all (including transitive) module dependencies for examination...");
                }
                target.forEach(dep -> {
                    // skip dependencies on project modules
                    if (Comparator.contains(allProjectDependencies, dep)) {
                        project.getLogger().debug("Skipping internal project dependency {} of configuration {}",
                                dep.toString(), configuration.getName());
                        return;
                    }
                    if (dep.getModuleGroup().isEmpty()) {
                        logger.warn("Ignoring dependency {} with no groupId for configuration {}",
                                dep.getName(), configuration.getName());
                        return;
                    }

                    String version = dep.getModuleVersion(); // this is the resolved version from gradle
                    // if the dependency is present in any of the lockfiles, then we use that version
                    for (ProjectVersionRef lockFileDep : lockFileDeps) {
                        if (lockFileDep.getGroupId().equals(dep.getModuleGroup())
                                && lockFileDep.getArtifactId().equals(dep.getModuleName())) {
                            version = lockFileDep.getVersionString();
                        }
                    }
                    ProjectVersionRef pvr = ProjectVersionFactory.withGAV(dep.getModuleGroup(), dep.getModuleName(),
                            version);

                    List<Dependency> originalDeps = allDependencies.stream()
                            .filter(d -> StringUtils.equals(d.getGroup(), dep.getModuleGroup()) &&
                                    StringUtils.equals(d.getName(), dep.getModuleName()))
                            .collect(Collectors.toList());

                    // Not sure this can ever happen - would mean we have GA with multiple V.
                    if (originalDeps.size() > 1) {
                        logger.error("Found duplicate matching original dependencies {} for {}", originalDeps, dep);
                    }

                    RelaxedProjectVersionRef relaxedProjectVersionRef;
                    // If we haven't found any original dependency, or its' version is empty, we'll default to
                    // the current resolved dependency value. This might be possible if the dependency has come from
                    // a lock file or the version comes from a BOM.
                    if (originalDeps.isEmpty() || StringUtils.isBlank(originalDeps.get(0).getVersion())) {
                        relaxedProjectVersionRef = new RelaxedProjectVersionRef(dep);
                    } else {
                        relaxedProjectVersionRef = new RelaxedProjectVersionRef(originalDeps.get(0));
                    }

                    if (depMap.put(relaxedProjectVersionRef, pvr) == null) {
                        logger.debug("For {}, with original key {}, adding dependency to scan {} ", configuration,
                                relaxedProjectVersionRef, pvr);
                    }

                });

            } else {
                logger.trace("Unable to resolve configuration {} for project {}", configuration.getName(), project);
            }
        });

        return depMap;
    }

    private Set<UnresolvedDependency> getUnresolvedDependenciesExcludingProjectDependencies(LenientConfiguration lenient,
            Set<ProjectDependency> allProjectModules) {
        return lenient.getUnresolvedModuleDependencies()
                .stream()
                .filter(d -> !Comparator.contains(allProjectModules, d))
                .collect(Collectors.toSet());
    }

    private void updateModuleDynamicDependencies(ManipulationModel correspondingModule,
            Map<RelaxedProjectVersionRef, ProjectVersionRef> allModuleDependencies) {

        allModuleDependencies.forEach((d, p) -> {
            // we need to make sure that dynamic dependencies are stored with their original key
            // in order for the manipulation plugin to be able to look them up properly
            if (isNotBlank(d.getVersionString()) && DynamicVersionParser.isDynamic(d.getVersionString())) {
                correspondingModule.getAlignedDependencies().put(d.toString(), p);
            }
        });
    }

    /**
     * This does the actual substitution replacing the dependencies with aligned version if it exists
     *
     * @param correspondingModule the module we are working on
     * @param allModuleDependencies the collection of dependencies
     * @param alignmentResponse the response which (possibly) contains overrides and DA information
     */
    private void updateModuleDependencies(ManipulationModel correspondingModule,
            Map<RelaxedProjectVersionRef, ProjectVersionRef> allModuleDependencies, Response alignmentResponse) {

        allModuleDependencies.forEach((d, p) -> {
            final String newDependencyVersion = alignmentResponse.getAlignedVersionOfGav(p);
            if (!StringUtils.isEmpty(newDependencyVersion)) {
                logger.debug("In module {} with GAV {} found a replacement version of {}",
                        correspondingModule.getProjectPathName(), p, newDependencyVersion);

                final ProjectVersionRef newVersion = ProjectVersionFactory.withNewVersion(p, newDependencyVersion);
                // we need to make sure that dynamic dependencies are stored with their original key
                // in order for the manipulation plugin to be able to look them up properly
                correspondingModule.getAlignedDependencies().put(d.toString(), newVersion);
            }
        });
    }

    /**
     * Writes a maven settings file containing artifact repositories used by this project.
     *
     * @param repositories A map of repositories to the file path where it occurred.
     */
    private void writeRepositorySettingsFile(Map<ArtifactRepository, Path> repositories) {
        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        String repositoriesFilePath = config.repositoriesFile();
        if (!isEmpty(repositoriesFilePath)) {
            File repositoriesFile;
            if (Paths.get(repositoriesFilePath).isAbsolute()) {
                repositoriesFile = new File(repositoriesFilePath);
            } else {
                repositoriesFile = new File(getProject().getRootDir(), repositoriesFilePath);
            }

            RepositoryExporter.export(repositories, repositoriesFile);
        } else {
            logger.info("Repository export disabled.");
        }
    }

    private Map<RelaxedProjectVersionRef, ProjectVersionRef> processAnyExistingManipulationFile(Project project,
            Map<RelaxedProjectVersionRef, ProjectVersionRef> allDependencies) {

        File m = new File(project.getRootDir(), ManipulationIO.MANIPULATION_FILE_NAME);

        if (!m.exists()) {
            return allDependencies;
        }

        // If there is an existing manipulation file, also use this as potential candidates.
        final ManipulationModel manipulationModel = ManipulationIO.readManipulationModel(project.getRootProject().getRootDir())
                .findCorrespondingChild(project);

        Map<String, ProjectVersionRef> aligned = manipulationModel.getAlignedDependencies();

        for (Map.Entry<String, ProjectVersionRef> modelDependencies : aligned.entrySet()) {

            // If we don't have 2 then we must be stored an unversioned artifact. Only interested in full GAV right now.
            if (StringUtils.countMatches(modelDependencies.getKey(), ":") == 2) {

                ProjectVersionRef originalPvr = SimpleProjectVersionRef.parse(modelDependencies.getKey());

                for (Map.Entry<RelaxedProjectVersionRef, ProjectVersionRef> entry : allDependencies.entrySet()) {

                    RelaxedProjectVersionRef d = entry.getKey();

                    if (d.equals(originalPvr)) {

                        if (!modelDependencies.getValue().getVersionString().equals(entry.getValue().getVersionString())) {

                            logger.info("Using existing model to update {} to {}", entry.getValue(),
                                    modelDependencies.getValue());

                            allDependencies.put(d, modelDependencies.getValue());
                            break;
                        }
                    }
                }
            }
        }
        return allDependencies;
    }

    private void writeReport(Path outputDir, String filename, String text) throws ManipulationException {
        final Path reportFile = outputDir.resolve(filename);

        try {
            logger.debug("Writing to file {}", reportFile);
            Files.createDirectories(outputDir);
            FileUtils.writeStringToFile(reportFile.toFile(), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ManipulationException("Unable to write " + reportFile, e);
        }
    }

    private void processAlignmentReport(Project project, Configuration configuration, ManipulationCache cache,
            Set<ProjectVersionRef> nonAligned) throws ManipulationException, IOException {
        final ManipulationModel alignmentModel = cache.getModel();
        final String originalGa = alignmentModel.getGroup() + ":" + alignmentModel.getName();
        final StringBuilder builder = new StringBuilder(500);
        append(builder, "------------------- project {}", originalGa);
        final String originalVersion = alignmentModel.getOriginalVersion();
        final String newVersion = alignmentModel.getVersion();
        final PME jsonReport = new PME();
        final List<ModulesItem> modules = jsonReport.getModules();
        final String gav = originalGa + ":" + newVersion;
        final ProjectVersionRef pvr = SimpleProjectVersionRef.parse(gav);
        final GAV g = new GAV();
        final String originalGav = originalGa + ":" + originalVersion;
        g.setOriginalGAV(originalGav);
        g.setPVR(pvr);
        jsonReport.setGav(g);

        if (!originalVersion.equals(newVersion)) {
            append(builder, "\tProject version : {} --> {}", originalVersion, newVersion);
            builder.append(System.lineSeparator());
        }

        final Map<Project, Map<RelaxedProjectVersionRef, ProjectVersionRef>> projectDependencies = cache
                .getDependencies();
        final Set<Map.Entry<Project, Map<RelaxedProjectVersionRef, ProjectVersionRef>>> entrySet = projectDependencies
                .entrySet();

        for (Map.Entry<Project, Map<RelaxedProjectVersionRef, ProjectVersionRef>> entry : entrySet) {
            final Project name = entry.getKey();
            final Map<RelaxedProjectVersionRef, ProjectVersionRef> allModuleDependencies = entry.getValue();
            final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(name);
            final String group = correspondingModule.getGroup().isEmpty() ? alignmentModel.getGroup()
                    : correspondingModule.getGroup();
            final String artifact = correspondingModule.getName();
            final String ga = group + ":" + artifact;
            final String v = correspondingModule.getOriginalVersion() == null ? originalVersion
                    : correspondingModule.getOriginalVersion();
            final String newModuleVersion = correspondingModule.getVersion();
            final String newModuleGav = ga + ":" + newModuleVersion;
            append(builder, "------------------- project {}", ga);

            if (!v.equals(newModuleVersion)) {
                append(builder, "\tProject version : {} --> {}", v, newModuleVersion);
            }

            builder.append(System.lineSeparator());
            final ModulesItem module = new ModulesItem();
            final String originalModuleGav = ga + ":" + v;
            module.getGav().setOriginalGAV(originalModuleGav);
            module.getGav().setPVR(SimpleProjectVersionRef.parse(newModuleGav));
            modules.add(module);
            final Map<String, ProjectVersionRef> dependencies = new LinkedHashMap<>();
            final boolean reportNonAligned = configuration.reportNonAligned();
            final Set<Map.Entry<RelaxedProjectVersionRef, ProjectVersionRef>> allModuleDependenciesEntrySet = allModuleDependencies
                    .entrySet();

            for (Map.Entry<RelaxedProjectVersionRef, ProjectVersionRef> e : allModuleDependenciesEntrySet) {
                final RelaxedProjectVersionRef d = e.getKey();
                final ProjectVersionRef p = e.getValue();
                final ProjectVersionRef newDependencyVersion = correspondingModule.getAlignedDependencies()
                        .get(d.toString());
                logger.debug("In module {} with GAV {} found a replacement version of {}",
                        correspondingModule.getProjectPathName(), p, newDependencyVersion);

                if (newDependencyVersion == null) {
                    if (reportNonAligned) {
                        nonAligned.add(d);
                    }
                } else {
                    dependencies.put(d.toString(), newDependencyVersion);
                }
            }

            if (!dependencies.isEmpty()) {
                module.getDependencies().putAll(dependencies);
                final Set<Map.Entry<String, ProjectVersionRef>> dependenciesEntrySet = dependencies.entrySet();

                for (Map.Entry<String, ProjectVersionRef> dependencyEntry : dependenciesEntrySet) {
                    final String p = dependencyEntry.getKey();
                    final ProjectVersionRef newDependencyVersion = dependencyEntry.getValue();
                    append(builder, "\tDependencies : {} --> {}", p, newDependencyVersion);
                }
            }

            if (!nonAligned.isEmpty()) {
                for (ProjectVersionRef na : nonAligned) {
                    append(builder, "\tNon-Aligned Dependencies : {}", na);
                }
            }

            if (!dependencies.isEmpty() || !nonAligned.isEmpty()) {
                builder.append(System.lineSeparator());
            }
        }

        final String reportText = builder.toString();
        logger.info("{}{}", System.lineSeparator(), reportText);
        final Path outputDir = project.getRootProject().getBuildDir().toPath();

        if (!StringUtils.isEmpty(configuration.reportTxtOutputFile())) {
            writeReport(outputDir, configuration.reportTxtOutputFile(), reportText);
        }

        if (!StringUtils.isEmpty(configuration.reportJsonOutputFile())) {
            writeReport(outputDir, configuration.reportJsonOutputFile(), JSONUtils.jsonToString(jsonReport));
        }
    }
}
