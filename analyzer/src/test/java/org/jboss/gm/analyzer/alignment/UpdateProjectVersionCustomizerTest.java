package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class UpdateProjectVersionCustomizerTest {

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion() {
        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getNewProjectVersion()).thenReturn(null);

        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer("1.0.0", "redhat", 3);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.0.0-redhat-001");
        });
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasProperProjectVersion() {
        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        final String originalProjectVersion = "1.0.0-redhat-001";
        when(originalResp.getNewProjectVersion()).thenReturn(originalProjectVersion);

        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer("whatever", "redhat", 3);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.0.0-redhat-002");
        });
    }

}