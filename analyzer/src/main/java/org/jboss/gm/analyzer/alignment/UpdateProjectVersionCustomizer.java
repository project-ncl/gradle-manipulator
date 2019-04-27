package org.jboss.gm.analyzer.alignment;

import java.util.Collections;
import java.util.Set;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationConverter;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.impl.VersionCalculator;
import org.commonjava.maven.ext.core.state.VersioningState;

/**
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.ResponseCustomizer} that changes the project version
 *
 * The heavy lifting is actually done by {@link org.commonjava.maven.ext.core.impl.VersionCalculator}
 */
public class UpdateProjectVersionCustomizer implements AlignmentService.ResponseCustomizer {

    private final ProjectVersionRef projectVersion;

    private final Configuration configuration;

    UpdateProjectVersionCustomizer(ProjectVersionRef projectVersion, Configuration configuration) {
        this.projectVersion = projectVersion;
        this.configuration = configuration;
    }

    @Override
    public int order() {
        return -10;
    }

    @Override
    public AlignmentService.Response customize(AlignmentService.Response response) {
        return new ProjectVersionCustomizerResponse(response, projectVersion, configuration);
    }

    private static class ProjectVersionCustomizerResponse implements AlignmentService.Response {
        private final GradleVersionCalculator vc = new GradleVersionCalculator();
        private final AlignmentService.Response originalResponse;
        private final ProjectVersionRef version;
        private final VersioningState state;

        ProjectVersionCustomizerResponse(AlignmentService.Response originalResponse, ProjectVersionRef version,
                Configuration configuration) {
            this.originalResponse = originalResponse;
            this.version = version;
            this.state = new VersioningState(ConfigurationConverter.getProperties(configuration));
        }

        @Override
        public String getNewProjectVersion() {
            try {
                return vc.calculate(version.getGroupId(), version.getArtifactId(), version.getVersionString(), state);
            } catch (ManipulationException e) {
                throw new ManipulationUncheckedException(e);
            }
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
                return (originalResponse.getNewProjectVersion() == null ? Collections.emptySet()
                        : Collections.singleton(originalResponse.getNewProjectVersion()));
            }
        }
    }
}
