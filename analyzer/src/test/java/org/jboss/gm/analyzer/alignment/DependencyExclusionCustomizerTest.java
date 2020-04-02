package org.jboss.gm.analyzer.alignment;

import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.versioning.ProjectVersionFactory.withGAV;

public class DependencyExclusionCustomizerTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void ensureExclusionOfDependenciesWorks() {
        final ProjectVersionRef project = withGAV("org.acme", "dummy", "1.0.0");
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0");

        final Predicate<ProjectRef> excludeHibernatePredicate = (gav -> !"org.hibernate".equals(gav.getGroupId()));
        final DependencyExclusionCustomizer sut = new DependencyExclusionCustomizer(excludeHibernatePredicate);

        final AlignmentService.Request originalReq = new AlignmentService.Request(Collections.singletonList(project),
                Stream.of(hibernateGav, undertowGav, mockitoGav).collect(Collectors.toSet()));
        final AlignmentService.Request customizedReq = sut.customize(originalReq);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getProject()).isEqualTo(Collections.singletonList(project));
            assertThat(r.getDependencies()).extracting("artifactId").containsOnly("undertow-core", "mockito-core");
        });
    }
}
