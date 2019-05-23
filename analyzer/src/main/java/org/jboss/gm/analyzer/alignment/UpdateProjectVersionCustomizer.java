package org.jboss.gm.analyzer.alignment;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.impl.VersionCalculator;
import org.commonjava.maven.ext.core.state.VersioningState;
import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.ResponseCustomizer} that changes the project version
 *
 * The heavy lifting is actually done by {@link org.commonjava.maven.ext.core.impl.VersionCalculator}
 */
public class UpdateProjectVersionCustomizer implements AlignmentService.ResponseCustomizer {

    private final Set<Project> projects;
    private final Configuration configuration;

    UpdateProjectVersionCustomizer(Set<Project> projects, Configuration configuration) {
        this.projects = projects;
        this.configuration = configuration;
    }

    @Override
    public int order() {
        return -10;
    }

    @Override
    public AlignmentService.Response customize(AlignmentService.Response response) {
        return new ProjectVersionCustomizerResponse(response, projects, configuration);
    }

    private static class ProjectVersionCustomizerResponse implements AlignmentService.Response {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private final GradleVersionCalculator vc = new GradleVersionCalculator();
        private final AlignmentService.Response originalResponse;
        private final VersioningState state;
        private final ManipulationCache cache;
        private final Project root;

        ProjectVersionCustomizerResponse(AlignmentService.Response originalResponse, Set<Project> projects,
                Configuration configuration) {
            this.originalResponse = originalResponse;

            Project tmp = projects.toArray(new Project[] {})[0].getRootProject();
            if (DefaultProject.DEFAULT_VERSION.equals(tmp.getVersion())) {
                // Root project has a non-valid version. Find another one to use.
                for (Project p : projects) {
                    if (!DefaultProject.DEFAULT_VERSION.equals(p.getVersion())) {
                        tmp = p;
                        break;
                    }
                }
            }
            root = tmp;

            cache = ManipulationCache.getCache(root);

            logger.info("Creating versioning state with {} and {} with original {} and version {}",
                    configuration.versionIncrementalSuffix(), configuration.versionIncrementalSuffixPadding(), originalResponse,
                    projects);
            this.state = new VersioningState(configuration.getProperties());
        }

        @Override
        public String getNewProjectVersion() {
            try {
                return vc.calculate(root.getGroup().toString(), root.getName(), root.getVersion().toString(), state);
            } catch (ManipulationException e) {
                throw new ManipulationUncheckedException(e);
            }
        }

        @Override
        public Map<ProjectVersionRef, String> getTranslationMap() {
            return originalResponse.getTranslationMap();
        }

        @Override
        public String getAlignedVersionOfGav(ProjectVersionRef gav) {
            return originalResponse.getAlignedVersionOfGav(gav);
        }

        private class GradleVersionCalculator extends VersionCalculator {
            GradleVersionCalculator() {
                super(null);
            }

            public String calculate(final String groupId, final String artifactId, final String version,
                    final VersioningState state) throws ManipulationException {
                return super.calculate(groupId, artifactId, version, state);
            }

            protected Set<String> getVersionCandidates(VersioningState state, String groupId, String artifactId) {

                final Set<String> result = new HashSet<>();

                cache.getGAV().forEach(pvr -> {
                    String t = getTranslationMap().get(pvr);
                    if (StringUtils.isNotEmpty(t)) {
                        result.add(t);
                    }
                });

                logger.debug("Translation map is using {}", result);

                return result;
            }
        }
    }
}
