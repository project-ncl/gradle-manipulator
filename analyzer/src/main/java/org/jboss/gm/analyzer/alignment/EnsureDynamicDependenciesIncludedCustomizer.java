package org.jboss.gm.analyzer.alignment;

import java.util.Map;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

/**
 * A specific version always needs to be stored in the output file for each dynamic dependency
 * If the alignment response does not contain the dependency, then the resolved (by Gradle) version
 * needs to be added
 * Dynamic dependencies need to be added to the alignment result in order to ensure reproducible builds
 */
public class EnsureDynamicDependenciesIncludedCustomizer implements AlignmentService.ResponseCustomizer {

    @Override
    public AlignmentService.Response customize(AlignmentService.Response response, AlignmentService.Request request) {
        return new EnsureDynamicDependenciesIncludedResponse(response, request);
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE - 100;
    }

    private static class EnsureDynamicDependenciesIncludedResponse implements AlignmentService.Response {

        private final AlignmentService.Response originalResponse;
        private final Set<? extends ProjectVersionRef> dynamicDependenciesWithGradleResolvedVersion;

        public EnsureDynamicDependenciesIncludedResponse(AlignmentService.Response originalResponse,
                AlignmentService.Request request) {
            this.originalResponse = originalResponse;
            this.dynamicDependenciesWithGradleResolvedVersion = request.getDynamicDependenciesWithGradleResolvedVersions();
        }

        @Override
        public String getNewProjectVersion() {
            return originalResponse.getNewProjectVersion();
        }

        @Override
        public Map<ProjectVersionRef, String> getTranslationMap() {
            return originalResponse.getTranslationMap();
        }

        @Override
        public String getAlignedVersionOfGav(ProjectVersionRef gav) {
            final String alignedVersion = originalResponse.getAlignedVersionOfGav(gav);
            if (alignedVersion != null) {
                return alignedVersion;
            }

            // by returning the string we ensure that the version will be added as an aligned dependency
            if (dynamicDependenciesWithGradleResolvedVersion.contains(gav)) {
                return gav.getVersionString();
            }

            return null;
        }
    }
}
