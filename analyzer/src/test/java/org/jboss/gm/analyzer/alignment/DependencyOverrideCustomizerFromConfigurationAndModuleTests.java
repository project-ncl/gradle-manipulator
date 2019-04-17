package org.jboss.gm.analyzer.alignment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyOverrideCustomizerFromConfigurationAndModuleTests {

	private static final GAV.Simple PROJECT = new GAV.Simple("org.acme", "test", "1.0.0-redhat-00001");

	@Test
	public void noDependencyOverrideProperty() {
		final Map<String, String> properties = new HashMap<>();
		final Configuration configuration = new MapConfiguration(properties);

		final AlignmentService.ResponseCustomizer sut =
				DependencyOverrideCustomizer.fromConfigurationForModule(configuration, PROJECT.toProjectVersionRef());

		assertThat(sut).isSameAs(AlignmentService.ResponseCustomizer.NOOP);
	}

	@Test
	public void erroneousPropertiesDontCauseFailure() {
		final Map<String, String> properties = new HashMap<String, String>() {{
			put("dependencyOverride.org.acme", "");
		}};
		final Configuration configuration = new MapConfiguration(properties);

		final AlignmentService.ResponseCustomizer sut =
				DependencyOverrideCustomizer.fromConfigurationForModule(configuration, PROJECT.toProjectVersionRef());

		assertThat(sut).isSameAs(AlignmentService.ResponseCustomizer.NOOP);
	}

	@Test
	public void ensureOverrideMatches() {
		final GAV.Simple hibernateCoreGav = new GAV.Simple("org.hibernate", "hibernate-core", "5.3.9.Final-redhat-00001");
		final GAV.Simple hibernateValidatorGav = new GAV.Simple("org.hibernate", "hibernate-validator", "6.0.16.Final-redhat-00001");
		final GAV.Simple undertowGav = new GAV.Simple("io.undertow", "undertow-core", "2.0.15.Final-redhat-00001");
		final GAV.Simple jacksonCoreGav = new GAV.Simple("com.fasterxml.jackson.core", "jackson-core", "2.9.8-redhat-00001");
		final GAV.Simple jacksonMapperGav = new GAV.Simple("com.fasterxml.jackson.core", "jackson-mapper", "2.9.8-redhat-00001");
		final GAV.Simple mongoGav = new GAV.Simple("org.mongodb", "mongo-java-driver", "3.10.2-redhat-00001");
		final GAV.Simple mockitoGav = new GAV.Simple("org.mockito", "mockito-core", "2.27.0-redhat-00001");
		final GAV.Simple wiremockGav = new GAV.Simple("com.github.tomakehurst", "wiremock-jre8", "2.23.2-redhat-00001");

		final Map<String, String> properties = new HashMap<String, String>() {{
			put("dependencyOverride.org.hibernate:hibernate-core@*", "5.3.7.Final-redhat-00001");  // should result in overriding only hibernate-core dependency
			put("dependencyOverride.com.fasterxml.jackson.core:*@*", "2.9.5-redhat-00001"); // should result in overriding all jackson dependencies
			put("dependencyOverride.io.undertow:undertow-servlet@*", "2.0.14.Final-redhat-00001"); // should NOT result in overriding the undertow dependency since the artifact doesn't match
			put("dependencyOverride.org.mockito:*@org.acme:test", "2.27.0-redhat-00002"); // should result in overriding the mockito dependency
			put("dependencyOverride.com.github.tomakehurst:*@org.acme:other", ""); // should NOT result overriding the wiremock dependency since the module doesn't match
		}};
		final Configuration configuration = new MapConfiguration(properties);


		final AlignmentService.ResponseCustomizer sut =
				DependencyOverrideCustomizer.fromConfigurationForModule(configuration, PROJECT.toProjectVersionRef());


		final AlignmentService.Response originalResp = new DummyResponse(PROJECT,
				Arrays.asList(hibernateCoreGav, hibernateValidatorGav, undertowGav, jacksonCoreGav, jacksonMapperGav,
						mongoGav, mockitoGav, wiremockGav));
		final AlignmentService.Response finalResp = sut.customize(originalResp);

		assertThat(finalResp).isNotNull().satisfies(r -> {
			assertThat(r.getNewProjectVersion()).isEqualTo(PROJECT.getVersion());
			assertThat(r.getAlignedVersionOfGav(hibernateCoreGav)).isEqualTo("5.3.7.Final-redhat-00001");
			assertThat(r.getAlignedVersionOfGav(hibernateValidatorGav)).isEqualTo(hibernateValidatorGav.getVersion());
			assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo(undertowGav.getVersion());
			assertThat(r.getAlignedVersionOfGav(jacksonCoreGav)).isEqualTo("2.9.5-redhat-00001");
			assertThat(r.getAlignedVersionOfGav(mockitoGav)).isEqualTo("2.27.0-redhat-00002");
			assertThat(r.getAlignedVersionOfGav(wiremockGav)).isEqualTo(wiremockGav.getVersion());
		});
	}

	private static class DummyResponse implements AlignmentService.Response {
		private final GAV project;
		private final Map<GAV, String> alignedVersionsMap = new HashMap<>();

		DummyResponse(GAV project, List<? extends GAV> dependencies) {
			this.project = project;
			dependencies.forEach(d -> {
				this.alignedVersionsMap.put(d, d.getVersion());
			});
		}


		@Override
		public String getNewProjectVersion() {
			return project.getVersion();
		}

		@Override
		public String getAlignedVersionOfGav(GAV gav) {
			return alignedVersionsMap.get(gav);
		}
	}
}