package org.jboss.gm.analyzer.alignment;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.ProjectVersionFactory.withGAV;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DependencyOverrideCustomizerTest {

    private static final String DEFAULT_SUFFIX = "-redhat-00001";

    @Test
    public void ensureOverrideOfDependenciesWorks() {
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");

        final String expectedHibernateVersion = "5.3.7.Final-redhat-00002";
        final DependencyOverrideCustomizer sut = new DependencyOverrideCustomizer(new HashMap<ProjectRef, String>() {
            {
                put(new SimpleProjectRef("org.hibernate", "*"), expectedHibernateVersion);
            }
        });

        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // the default behavior of the response will be to add '-redhat-00001' suffix
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString() + DEFAULT_SUFFIX;
        });
        final String newProjectVersion = "1.0.0" + DEFAULT_SUFFIX;
        when(originalResp.getNewProjectVersion()).thenReturn(newProjectVersion);

        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo(newProjectVersion);
            // make sure the matched dependency's version has changed
            assertThat(r.getAlignedVersionOfGav(hibernateGav)).isEqualTo(expectedHibernateVersion);
            // make sure that non matched dependencies still return their original value
            assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo(undertowGav.getVersionString());
        });
    }

}