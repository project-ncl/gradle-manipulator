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
 * {@link AlignmentService.Manipulator} that changes the project version
 *
 * The heavy lifting is actually done by {@link org.commonjava.maven.ext.core.impl.VersionCalculator}
 */
public class UpdateProjectVersionCustomizer implements AlignmentService.Manipulator {
    private final VersioningState state;
    private final ManipulationCache cache;
    private final Project project;

    private final Logger logger = GMLogger.getLogger(getClass());

    private final GradleVersionCalculator vc = new GradleVersionCalculator();

    public UpdateProjectVersionCustomizer(Configuration configuration, Project rootProject) {
        this.cache = ManipulationCache.getCache(rootProject);
        state = new VersioningState(configuration.getProperties());

        if (DefaultProject.DEFAULT_VERSION.equals(rootProject.getVersion())) {
            project = rootProject.getAllprojects().stream().filter(f -> !DefaultProject.DEFAULT_VERSION.equals(f.getVersion()))
                    .findFirst()
                    .orElseThrow(() -> new ManipulationUncheckedException("Unable to find suitable project version"));
        } else {
            project = rootProject;
        }

        logger.info("Creating versioning state with {} and {}", configuration.versionIncrementalSuffix(),
                configuration.versionIncrementalSuffixPadding());
    }

    @Override
    public int order() {
        return -10;
    }

    @Override
    public void customize(Response response) throws ManipulationException {

        vc.translationMap = response.getTranslationMap();
        response.setNewProjectVersion(
                vc.calculate(project.getGroup().toString(), project.getName(), project.getVersion().toString(),
                        state));
    }

    private class GradleVersionCalculator extends VersionCalculator {
        private Map<ProjectVersionRef, String> translationMap = null;

        GradleVersionCalculator() {
            super(null);
        }

        public String calculate(final String groupId, final String artifactId, final String version,
                final VersioningState state) throws ManipulationException {
            return super.calculate(groupId, artifactId, version, state);
        }

        @Override
        protected Set<String> getVersionCandidates(VersioningState state, String groupId, String artifactId) {

            final Set<String> result = new HashSet<>();

            // If there is an existing manipulation file, also use this as potential candidates.
            File m = new File(project.getRootDir(), ManipulationIO.MANIPULATION_FILE_NAME);
            if (m.exists()) {
                result.add(ManipulationIO.readManipulationModel(project.getRootDir()).getVersion());
            }
            logger.debug("Adding project version candidates from cache {}",
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
