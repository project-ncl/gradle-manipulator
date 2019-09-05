package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.versioning.ProjectVersionFactory.withGAV;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class DependencyOverrideCustomizerTest {

    private static final String DEFAULT_SUFFIX = "-redhat-00001";

    @Test
    public void ensureOverrideOfDependenciesWorks() {
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");
        // this gav will not be part of the original response
        final ProjectVersionRef jacksonGav = withGAV("com.fasterxml.jackson.core", "undertow-core", "2.9.9.3");

        // make a customizer that overrides any hibernate dependency but no other gavs
        final DependencyOverrideCustomizer sut = new DependencyOverrideCustomizer(Collections.singletonMap(
                new SimpleProjectRef("org.hibernate", "*"), "5.3.10.Final-redhat-00001"));

        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);

        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            // ensure that jackson is not included in the original response
            if (input.getGroupId().equals(jacksonGav.getGroupId())) {
                return null;
            }
            // the default behavior of the response will be to add '-redhat-00001' suffix
            return input.getVersionString() + DEFAULT_SUFFIX;
        });
        final String newProjectVersion = "1.0.0" + DEFAULT_SUFFIX;
        when(originalResp.getNewProjectVersion()).thenReturn(newProjectVersion);

        // here we simply ensure that our original response has been properly setup
        assertThat(originalResp).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo(newProjectVersion);
            assertThat(r.getAlignedVersionOfGav(hibernateGav)).isEqualTo("5.3.7.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo("2.0.15.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(jacksonGav)).isEqualTo(null);
        });

        final AlignmentService.Response customizedResp = sut.customize(originalResp);
        assertThat(customizedResp).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo(newProjectVersion);
            // make sure the matched dependency's version was changed by the dependency override customizer
            assertThat(r.getAlignedVersionOfGav(hibernateGav)).isEqualTo("5.3.10.Final-redhat-00001");

            // make sure that a gav aligned by the original response still yields with the original aligned version
            assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo("2.0.15.Final-redhat-00001");

            // make sure that a gav that was NOT aligned by the original response
            // does not return an aligned version after the dependency override customization is applied
            assertThat(r.getAlignedVersionOfGav(jacksonGav)).isEqualTo(null);
        });
    }

}
