package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.impl.VersionCalculator;
import org.commonjava.maven.ext.core.state.VersioningState;
import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.logging.Logger;
import org.jboss.gm.analyzer.alignment.AlignmentService.Response;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.logging.GMLogger;

/**
 * {@link AlignmentService.Manipulator} that changes the project version.
 * <br>
 * The heavy lifting is actually done by {@link org.commonjava.maven.ext.core.impl.VersionCalculator}
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
                    String version = vc.calculate(
                            project.getGroup().toString(),
                            project.getName(),
                            project.getVersion().toString(),
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

        // Set any that are 'unspecified' to the default project version.
        rootProject.getAllprojects()
                .stream()
                .filter(f -> DefaultProject.DEFAULT_VERSION.equals(f.getVersion()))
                .forEach(p -> projectsToVersions.put(p, newVersion[0]));
    }

    private class GradleVersionCalculator extends VersionCalculator {
        private Map<ProjectVersionRef, String> translationMap = null;

        GradleVersionCalculator() {
            super(null);
        }

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
