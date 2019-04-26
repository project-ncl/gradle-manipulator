package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration2.Configuration;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TestRule;
import org.mockito.stubbing.Answer;

public class UpdateProjectVersionCustomizerTest {

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion() {
        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getNewProjectVersion()).thenReturn(null);

        ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org:dummy:1.0.0");
        Configuration configuration = ConfigurationFactory.getConfiguration();
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(pvr, configuration);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.0.0.redhat-00001");
        });
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion2() {
        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getNewProjectVersion()).thenReturn(null);

        ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org:dummy:1.1");
        Configuration configuration = ConfigurationFactory.getConfiguration();
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(pvr, configuration);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00001");
        });
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion3() {

        System.setProperty("versionIncrementalSuffixPadding", "3");

        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getNewProjectVersion()).thenReturn(null);

        ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org:dummy:1.1");
        Configuration configuration = ConfigurationFactory.getConfiguration();
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(pvr, configuration);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-001");
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
        /*
         * final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer("1.0.0", "redhat", 3);
         * final AlignmentService.Response customizedReq = sut.customize(originalResp);
         * 
         * assertThat(customizedReq).isNotNull().satisfies(r -> {
         * assertThat(r.getNewProjectVersion()).isEqualTo("1.0.0.redhat-002");
         * });
         */
    }

}