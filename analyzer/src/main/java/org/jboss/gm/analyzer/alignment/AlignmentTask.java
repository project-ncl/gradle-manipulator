package org.jboss.gm.analyzer.alignment;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.gradle.api.Project.DEFAULT_VERSION;
import static org.jboss.gm.common.io.ManipulationIO.writeManipulationModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.impl.Version;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.analyzer.alignment.groovy.GMEBaseScript;
import org.jboss.gm.analyzer.alignment.io.LockFileIO;
import org.jboss.gm.analyzer.alignment.io.RepositoryExporter;
import org.jboss.gm.analyzer.alignment.io.SettingsFileIO;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.versioning.DynamicVersionParser;
import org.jboss.gm.common.versioning.ProjectVersionFactory;
import org.jboss.gm.common.versioning.RelaxedProjectVersionRef;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

/**
 * The actual Gradle task that creates the {@code manipulation.json} file for the whole project
 * (whether it's a single or multi module project)
 */
public class AlignmentTask extends DefaultTask {

    static final String INJECT_GME_START = "buildscript { apply from: \"gme.gradle\" }";
    static final String GME = "gme.gradle";
    static final String GRADLE = "gradle";
    static final String GME_REPOS = "gme-repos.gradle";
    static final String APPLY_GME_REPOS = "buildscript { apply from: new File(buildscript.getSourceFile().getParentFile(),\"gme-repos.gradle\"), to: buildscript }";
    static final String INJECT_GME_END = "apply from: \"gme-pluginconfigs.gradle\"";
    static final String GME_PLUGINCONFIGS = "gme-pluginconfigs.gradle";
    static final String NAME = "generateAlignmentMetadata";

    private static final AtomicBoolean configOutput = new AtomicBoolean();

    private final Logger logger = GMLogger.getLogger(getClass());

    @TaskAction
    public void perform() {
        final Project project = getProject();
        final String projectName = project.getName();
        final Configuration configuration = ConfigCache.getOrCreate(Configuration.class);
        final ManipulationCache cache = ManipulationCache.getCache(project);

        if (configOutput.compareAndSet(false, true)) {
            // Only output the config once to avoid noisy logging.
            configuration.dumpCurrentConfig(logger);
        }
        logger.info("Starting model task for project {} with GAV {}:{}:{}", project.getDisplayName(), project.getGroup(),
                projectName, project.getVersion());

        try {
            final Set<ProjectVersionRef> lockFileDeps = LockFileIO
                    .allProjectVersionRefsFromLockfiles(getLocksRootPath(project));
            final String currentProjectVersion = project.getVersion().toString();
            final HashMap<RelaxedProjectVersionRef, ProjectVersionRef> dependencies = processAnyExistingManipulationFile(
                    project,
                    getDependencies(project, configuration, lockFileDeps));

            cache.addDependencies(project, dependencies);

            Path root = project.getRootDir().toPath();
            project.getRepositories().forEach(r -> cache.addRepository(r,
                    org.jboss.gm.common.utils.FileUtils.relativize(root, project.getProjectDir().toPath())));
            project.getBuildscript().getRepositories().forEach(r -> cache.addRepository(r,
                    org.jboss.gm.common.utils.FileUtils.relativize(root, project.getProjectDir().toPath())));

            if (StringUtils.isBlank(project.getGroup().toString()) ||
                    DEFAULT_VERSION.equals(project.getVersion().toString())) {

                logger.warn("Project '{}:{}:{}' is not fully defined ; skipping. ", project.getGroup(), projectName,
                        project.getVersion());
            } else {
                ProjectVersionRef current = ProjectVersionFactory.withGAV(project.getGroup().toString(), projectName,
                        currentProjectVersion);

                logger.debug("Adding {} to cache for scanning.", current);
                cache.addGAV(project, current);
            }

            // when the set is empty, we know that this was the last alignment task to execute.
            if (cache.removeProject(projectName)) {
                logger.info("Completed scanning projects; now processing for REST...");
                Collection<ProjectVersionRef> allDeps = cache.getDependencies().values().stream()
                        .flatMap(m -> m.values().stream()).distinct().collect(Collectors.toList());

                final AlignmentService alignmentService = AlignmentServiceFactory
                        .getAlignmentService(cache.getDependencies().keySet());

                final AlignmentService.Response alignmentResponse = alignmentService.align(
                        new AlignmentService.Request(cache.getGAV().stream()
                                .map(p -> new SimpleProjectVersionRef(p.asProjectRef(),
                                        !configuration.versionSuffixSnapshot() ? Version.removeSnapshot(p.getVersionString())
                                                : p.getVersionString()))
                                .collect(Collectors.toList()), allDeps));

                final ManipulationModel alignmentModel = cache.getModel();
                final HashMap<Project, HashMap<RelaxedProjectVersionRef, ProjectVersionRef>> projectDependencies = cache
                        .getDependencies();
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
                    final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(key);
                    if (configuration.versionModificationEnabled()) {
                        logger.info("Updating sub-project {} version to {} ", correspondingModule, newVersion);
                        correspondingModule.setVersion(newVersion);
                    }
                    updateModuleDynamicDependencies(correspondingModule, value);
                    updateModuleDependencies(correspondingModule, value, alignmentResponse);
                });

                logger.info("Completed processing for alignment and writing {} ", cache.toString());

                // artifactId / rootProject.getName
                final String artifactId = SettingsFileIO.writeProjectNameIfNeeded(getProject().getRootDir());
                if (!isEmpty(artifactId)) {
                    logger.debug("Located artifactId ({}) for {}::{}", artifactId, alignmentModel.getGroup(),
                            alignmentModel.getVersion());
                    alignmentModel.setName(artifactId);
                }

                // groupId
                if (isEmpty(alignmentModel.getGroup())) {
                    String commonPrefix = StringUtils.stripEnd(
                            StringUtils.getCommonPrefix(
                                    alignmentModel.getChildren().values().stream().map(ManipulationModel::getGroup)
                                            .collect(Collectors.toList()).toArray(new String[] {})),
                            ".");

                    if (isEmpty(commonPrefix)) {
                        throw new ManipulationException(
                                "Empty groupId but unable to determine a suitable replacement from any child modules.");
                    }

                    logger.warn("groupId for {} ({}) is empty. Defaulting to common prefix of '{}'", project.getRootProject(),
                            project.getProjectDir(), commonPrefix);
                    alignmentModel.setGroup(commonPrefix);
                }

                runCustomGroovyScript(configuration, project.getRootProject(), alignmentModel);

                writeManipulationModel(project.getRootDir(), alignmentModel);
                // Ordering is important here ; we mustn't inject the gme-repos file before iterating over all *.gradle
                // files.
                updateAllExtraGradleFilesWithGmeRepos();
                writeGmeMarkerFile();
                writeGmeReposMarkerFile();
                writeGmeConfigMarkerFile();
                writeRepositorySettingsFile(cache.getRepositories());
            }

            // this needs to happen for each project, not just the last one
            LockFileIO.renameAllLockFiles(getLocksRootPath(project));

        } catch (ManipulationException e) {
            throw new ManipulationUncheckedException(e);
        } catch (IOException e) {
            throw new ManipulationUncheckedException("Failed to write marker file", e);
        }
    }

    // TODO: we might need to make this configurable
    private Path getLocksRootPath(Project project) {
        return project.getProjectDir().toPath().resolve("gradle/dependency-locks");
    }

    private void writeGmeMarkerFile() throws IOException, ManipulationException {
        File rootDir = getProject().getRootDir();
        File gmeGradle = new File(rootDir, GME);
        File rootGradle = new File(rootDir, Project.DEFAULT_BUILD_FILE);

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

    private void writeGmeConfigMarkerFile() throws IOException {
        File rootDir = getProject().getRootDir();
        File gmeGradle = new File(rootDir, GME_PLUGINCONFIGS);
        File rootGradle = new File(rootDir, Project.DEFAULT_BUILD_FILE);

        if (!gmeGradle.exists()) {
            Files.copy(getClass().getResourceAsStream('/' + GME_PLUGINCONFIGS), gmeGradle.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        if (rootGradle.exists()) {

            String line = org.jboss.gm.common.utils.FileUtils.getLastLine(rootGradle);
            logger.debug("Read last line '{}' from build.gradle", line);

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

    private HashMap<RelaxedProjectVersionRef, ProjectVersionRef> getDependencies(Project project, Configuration internalConfig,
            Set<ProjectVersionRef> lockFileDeps) {

        final HashMap<RelaxedProjectVersionRef, ProjectVersionRef> depMap = new HashMap<>();
        project.getConfigurations().all(configuration -> {
            if (configuration.isCanBeResolved()) {

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
                        for (UnresolvedDependency ud : unresolvedDependencies) {
                            logger.error("Unresolved had problem in {} with ", ud.getSelector(), ud.getProblem());
                        }
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
                    ProjectVersionRef pvr = ProjectVersionFactory.withGAV(dep.getModuleGroup(), dep.getModuleName(), version);

                    List<Dependency> originalDeps = allDependencies.stream()
                            .filter(d -> StringUtils.equals(d.getGroup(), dep.getModuleGroup()) &&
                                    StringUtils.equals(d.getName(), dep.getModuleName()))
                            .collect(Collectors.toList());

                    // Not sure this can ever happen - would mean we have GA with multiple V.
                    if (originalDeps.size() > 1) {
                        logger.error("Found duplicate matching original dependencies {} for {}", originalDeps, dep);
                    }

                    RelaxedProjectVersionRef relaxedProjectVersionRef;
                    // If we haven't found any original dependency we'll default to the current resolved dependency
                    // value. This might be possible if the dependency has come from a lock file.
                    if (originalDeps.size() == 0) {
                        relaxedProjectVersionRef = new RelaxedProjectVersionRef(dep);
                    } else {
                        relaxedProjectVersionRef = new RelaxedProjectVersionRef(originalDeps.get(0));
                    }

                    // TODO: What if originalDep has an empty version - then its from the BOM. Should we record it at all?
                    // if (StringUtils.isNotBlank(originalDep.getVersion())) {

                    if (depMap.put(relaxedProjectVersionRef, pvr) == null) {
                        logger.debug("For {}, with original key {}, adding dependency to scan {} ", configuration,
                                relaxedProjectVersionRef, pvr);
                    }

                });
            } else {
                logger.debug("Unable to resolve configuration {} for project {}", configuration.getName(), project);
            }
        });

        return depMap;
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

    private void updateModuleDynamicDependencies(ManipulationModel correspondingModule,
            HashMap<RelaxedProjectVersionRef, ProjectVersionRef> allModuleDependencies) {

        allModuleDependencies.forEach((d, p) -> {
            // we need to make sure that dynamic dependencies are stored with their original key
            // in order for the manipulation plugin to be able to look them up properly
            if (isNotBlank(d.getVersionString()) && DynamicVersionParser.isDynamic(d.getVersionString())) {
                correspondingModule.getAlignedDependencies().put(d.toString(), p);
            }
        });
    }

    private void updateModuleDependencies(ManipulationModel correspondingModule,
            HashMap<RelaxedProjectVersionRef, ProjectVersionRef> allModuleDependencies,
            AlignmentService.Response alignmentResponse) {

        allModuleDependencies.forEach((d, p) -> {
            final String newDependencyVersion = alignmentResponse.getAlignedVersionOfGav(p);
            if (!StringUtils.isEmpty(newDependencyVersion)) {
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

    private HashMap<RelaxedProjectVersionRef, ProjectVersionRef> processAnyExistingManipulationFile(Project project,
            HashMap<RelaxedProjectVersionRef, ProjectVersionRef> allDependencies) {

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

    private void runCustomGroovyScript(Configuration configuration, Project rootProject, ManipulationModel alignmentModel)
            throws IOException, ManipulationException {

        final List<File> groovyFiles = new ArrayList<>();
        final String[] scripts = configuration.groovyScripts();

        if (scripts != null) {
            int i = 0;
            for (String script : scripts) {
                logger.info("Attempting to read URL {} ", script);
                File remote = new File(rootProject.getRootDir(), "gme-" + i + "groovy");
                FileUtils.copyURLToFile(new URL(script), remote);
                groovyFiles.add(remote);
            }
        }

        for (File scriptFile : groovyFiles) {

            if (scriptFile.exists()) {
                final Binding binding = new Binding();
                // We use the current class' classloader so the script has access to this plugin's API and the
                // groovy API.
                final GroovyShell groovyShell = new GroovyShell(this.getClass().getClassLoader(), binding);
                final Script script = groovyShell.parse(scriptFile);

                logger.info("Attempting to invoke groovy script {} ", scriptFile);

                // Inject the values via a new BaseScript so user's can have completion.
                if (script instanceof GMEBaseScript) {
                    ((GMEBaseScript) script).setValues(rootProject, alignmentModel);
                } else {
                    throw new ManipulationException("Cannot cast " + script + " to a BaseScript to set values.");
                }
                script.run();
            }
        }
    }
}
