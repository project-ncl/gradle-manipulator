package org.jboss.gm.analyzer.alignment;

import java.util.Arrays;
import java.util.function.Predicate;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyExclusionCustomizerTests {

	@Test
	public void ensureExclusionOfDependenciesWorks() {
		final GAV.Simple project = new GAV.Simple("org.acme", "dummy", "1.0.0");
		final GAV.Simple hibernateGav = new GAV.Simple("org.hibernate", "hibernate-core", "5.3.7.Final");
		final GAV.Simple undertowGav = new GAV.Simple("io.undertow", "undertow-core", "2.0.15.Final");
		final GAV.Simple mockitoGav = new GAV.Simple("org.mockito", "mockito-core", "2.27.0");


		final Predicate<GAV> excludeHibernatePredicate = (gav -> !"org.hibernate".equals(gav.getGroup()));
		final DependencyExclusionCustomizer sut = new DependencyExclusionCustomizer(excludeHibernatePredicate);

		final AlignmentService.Request originalReq = new AlignmentService.Request(project,
				Arrays.asList(hibernateGav, undertowGav, mockitoGav));
		final AlignmentService.Request customizedReq = sut.customize(originalReq);

		assertThat(customizedReq).isNotNull().satisfies(r -> {
			assertThat(r.getProject()).isEqualTo(project);
			assertThat(r.getDependencies()).extracting("name").containsOnly("undertow-core", "mockito-core");
		});
	}
}