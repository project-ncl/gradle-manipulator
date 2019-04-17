package org.jboss.gm.analyzer.alignment;

import java.util.function.Predicate;

import org.junit.Test;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DependencyOverrideCustomizerTests {

	protected static final String DEFAULT_SUFFIX = "-redhat-00001";

	@Test
	public void ensureOverrideOfDependenciesWorks() {
		final GAV.Simple hibernateGav = new GAV.Simple("org.hibernate", "hibernate-core", "5.3.7.Final");
		final GAV.Simple undertowGav = new GAV.Simple("io.undertow", "undertow-core", "2.0.15.Final");


		final Predicate<GAV> overrideHibernatePredicate = (gav -> "org.hibernate".equals(gav.getGroup()));
		final String expectedHibernateVersion = "5.3.7.Final-redhat-00002";
		final DependencyOverrideCustomizer sut =
				new DependencyOverrideCustomizer(overrideHibernatePredicate, expectedHibernateVersion, 0);

		final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
		// the default behavior of the response will be to add '-redhat-00001' suffix
		when(originalResp.getAlignedVersionOfGav(any(GAV.class))).thenAnswer((Answer<String>) invocation -> {
			final GAV input = (GAV) invocation.getArguments()[0];
			return input.getVersion() + DEFAULT_SUFFIX;
		});
		final String newProjectVersion = "1.0.0" + DEFAULT_SUFFIX;
		when(originalResp.getNewProjectVersion()).thenReturn(newProjectVersion);


		final AlignmentService.Response customizedReq = sut.customize(originalResp);

		assertThat(customizedReq).isNotNull().satisfies(r -> {
			assertThat(r.getNewProjectVersion()).isEqualTo(newProjectVersion);
			// make sure the matched dependency's version has changed
			assertThat(r.getAlignedVersionOfGav(hibernateGav)).isEqualTo(expectedHibernateVersion);
			// make sure that non matched dependencies still return their original value
			assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo(undertowGav.getVersion());
		});
	}

}