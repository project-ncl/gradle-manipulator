package org.jboss.gm.analyzer.alignment;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

/**
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.ResponseCustomizer} that changes the project version
 *
 * The heavy lifting is actually done by {@link org.jboss.gm.analyzer.alignment.VersionWithSuffixUtils}
 */
public class UpdateProjectVersionCustomizer implements AlignmentService.ResponseCustomizer {

    private final String projectVersion;
    private final String suffixName;
    private final int suffixPaddingCount;

    UpdateProjectVersionCustomizer(String projectVersion, String suffixName, int suffixPaddingCount) {
        this.projectVersion = projectVersion;
        this.suffixName = suffixName;
        this.suffixPaddingCount = suffixPaddingCount;
    }

    @Override
    public int order() {
        return -10;
    }

    @Override
    public AlignmentService.Response customize(AlignmentService.Response response) {
        return new NewProjectVersionCustomizerResponse(response, projectVersion, suffixName, suffixPaddingCount);
    }

    private static class NewProjectVersionCustomizerResponse implements AlignmentService.Response {

        private final AlignmentService.Response originalResponse;
        private final String projectVersion;
        private final String suffixName;
        private final int suffixPaddingCount;

        NewProjectVersionCustomizerResponse(AlignmentService.Response originalResponse, String projectVersion,
                String suffixName,
                int suffixPaddingCount) {
            this.originalResponse = originalResponse;
            this.projectVersion = projectVersion;
            this.suffixName = suffixName;
            this.suffixPaddingCount = suffixPaddingCount;
        }

        @Override
        public String getNewProjectVersion() {
            final String projectVersionToUse = originalResponse.getNewProjectVersion() != null
                    ? originalResponse.getNewProjectVersion()
                    : projectVersion;
            return VersionWithSuffixUtils.getNextVersion(projectVersionToUse, suffixName,
                    suffixPaddingCount);
        }

        @Override
        public String getAlignedVersionOfGav(ProjectVersionRef gav) {
            return originalResponse.getAlignedVersionOfGav(gav);
        }
    }
}
