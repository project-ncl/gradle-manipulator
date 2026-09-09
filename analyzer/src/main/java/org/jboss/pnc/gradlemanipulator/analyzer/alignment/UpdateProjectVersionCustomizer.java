package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.logging.Logger;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.AlignmentService.Response;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.ManipulationCache;
import org.jboss.pnc.gradlemanipulator.common.io.ManipulationIO;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationUncheckedException;
import org.jboss.pnc.mavenmanipulator.core.impl.VersionCalculator;
import org.jboss.pnc.mavenmanipulator.core.state.VersioningState;

/**
 * {@link AlignmentService.Manipulator} that changes the project version.
 * <br>
 * The heavy lifting is actually done by {@link org.jboss.pnc.mavenmanipulator.core.impl.VersionCalculator}
 */
public class UpdateProjectVersionCustomizer implements AlignmentService.Manipulator {
    private final Configuration configuration;
    private VersioningState state;
    private ManipulationCache cache;
    private final Project rootProject;

    private final Logger logger = GMLogger.getLogger(getClass());

    private final GradleVersionCalculator vc = new GradleVersionCalculator();

    public UpdateProjectVersionCustomizer(Configuration configuration, Project rootProject) {
        this.configuration = configuration;
        this.rootProject = rootProject;

        if (!configuration.versionModificationEnabled()) {
            return;
        }

        this.cache = ManipulationCache.getCache(rootProject);
        state = new VersioningState(configuration.getProperties());

        logger.info(
                "Creating versioning state with {} and {}",
                configuration.versionIncrementalSuffix(),
                configuration.versionIncrementalSuffixPadding());
    }

    @Override
    public int order() {
        return -10;
    }

    @Override
    public void customize(Response response) throws ManipulationException {

        Map<Project, String> projectsToVersions = response.getProjectOverrides();
        String[] newVersion = { null }; // Array as used in lambda later.

        // For every module, calculate its version. This allows for the scenario
        // where a submodule has a different version to the rest of the project.
        for (Project project : rootProject.getAllprojects()) {
            if (!DefaultProject.DEFAULT_VERSION.equals(project.getVersion())) {
                if (configuration.versionModificationEnabled()) {
                    vc.translationMap = response.getTranslationMap();
                    // Use the version that was actually sent to DA for this project. When a prior
                    // manipulation.json exists, AlignmentTask replaces the project GAV in the cache
                    // with the file version (e.g. 1.0.1.rhlw-00001) rather than the raw Gradle
                    // version (e.g. 1.0.1). Using the cache PVR version here ensures the calculator
                    // receives the same base that DA used, so the suffix is appended on top of the
                    // full prior version rather than the clean source version.
                    final String groupId = project.getGroup().toString();
                    final String artifactId = project.getName();
                    final String calculatorInput = cache.getProjectVersionRefs(state.isPreserveSnapshot())
                            .stream()
                            .filter(
                                    pvr -> pvr.getGroupId().equals(groupId)
                                            && pvr.getArtifactId().equals(artifactId))
                            .map(ProjectVersionRef::getVersionString)
                            .findFirst()
                            .orElse(project.getVersion().toString());
                    if (!calculatorInput.equals(project.getVersion().toString())) {
                        logger.info(
                                "Using cached REST input version '{}' (instead of Gradle version '{}') "
                                        + "as calculator input for '{}'.",
                                calculatorInput,
                                project.getVersion(),
                                project.getPath());
                    }
                    String version = vc.calculate(
                            groupId,
                            artifactId,
                            calculatorInput,
                            state);
                    projectsToVersions.put(project, version);
                    if (newVersion[0] == null) {
                        newVersion[0] = version;
                    }
                } else {
                    projectsToVersions.put(project, project.getVersion().toString());
                    logger.info(
                            "Version modification is disabled. Not updating project {}:{} version {}",
                            rootProject.getGroup(),
                            rootProject.getName(),
                            project.getVersion().toString());
                }
            }
        }

        // Previously this set any modules that are 'unspecified' to the default project
        // version. With NCLSUP1402 the AlignmentTask::perform now updates project
        // versions while also handling those projects with no publications. This check
        // should therefore no longer be required.
        // However, as its theoretically possible to disable the changes via the
        // "scanProjectsWithNoPublications" property I'll keep this check as it should be
        // harmless.
        rootProject.getAllprojects()
                .stream()
                .filter(f -> DefaultProject.DEFAULT_VERSION.equals(f.getVersion()))
                .forEach(project -> {
                    // This should not happen but update the version anyway.
                    logger.error(
                            "Found project with unspecified version. Module: {} in directory {}",
                            project,
                            project.getProjectDir().getPath());
                    projectsToVersions.put(project, newVersion[0]);
                });
    }

    private class GradleVersionCalculator extends VersionCalculator {
        private Map<ProjectVersionRef, String> translationMap = null;

        public String calculate(
                final String groupId,
                final String artifactId,
                final String version,
                final VersioningState state) throws ManipulationException {
            return super.calculate(groupId, artifactId, version, state);
        }

        @Override
        protected Set<String> getVersionCandidates(VersioningState state, String groupId, String artifactId) {

            final Set<String> result = new HashSet<>();

            // If there is an existing manipulation file, also use this as potential candidates.
            File m = new File(rootProject.getRootDir(), ManipulationIO.MANIPULATION_FILE_NAME);
            if (m.exists()) {
                result.add(ManipulationIO.readManipulationModel(rootProject.getRootDir()).getVersion());
            }
            logger.debug(
                    "Adding project version candidates from cache {}",
                    cache.getProjectVersionRefs(state.isPreserveSnapshot()));

            if (translationMap == null) {
                throw new ManipulationUncheckedException("Translation map has not been initialised");
            }

            cache.getProjectVersionRefs(state.isPreserveSnapshot()).forEach(pvr -> {
                String t = translationMap.get(pvr);
                if (StringUtils.isNotBlank(t)) {
                    result.add(t.trim());
                }
            });

            logger.debug("Translation map is using {}", result);

            return result;
        }
    }
}
