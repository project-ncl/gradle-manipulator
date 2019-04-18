package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.ProjectVersionFactory.withGAV;

import java.util.Arrays;
import java.util.function.Predicate;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.junit.Test;

public class DependencyExclusionCustomizerTest {

    @Test
    public void ensureExclusionOfDependenciesWorks() {
        final ProjectVersionRef project = withGAV("org.acme", "dummy", "1.0.0");
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0");

        final Predicate<ProjectVersionRef> excludeHibernatePredicate = (gav -> !"org.hibernate".equals(gav.getGroupId()));
        final DependencyExclusionCustomizer sut = new DependencyExclusionCustomizer(excludeHibernatePredicate);

        final AlignmentService.Request originalReq = new AlignmentService.Request(project,
                Arrays.asList(hibernateGav, undertowGav, mockitoGav));
        final AlignmentService.Request customizedReq = sut.customize(originalReq);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getProject()).isEqualTo(project);
            assertThat(r.getDependencies()).extracting("artifactId").containsOnly("undertow-core", "mockito-core");
        });
    }
}