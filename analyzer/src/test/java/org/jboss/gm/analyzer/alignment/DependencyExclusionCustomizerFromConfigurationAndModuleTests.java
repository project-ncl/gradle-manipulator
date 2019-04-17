package org.jboss.gm.analyzer.alignment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyExclusionCustomizerFromConfigurationAndModuleTests {

	private static final ProjectRef PROJECT = new SimpleProjectRef("org.acme", "test");

	@Test
	public void noDependencyExclusionProperty() {
		final Map<String, String> properties = new HashMap<>();
		final Configuration configuration = new MapConfiguration(properties);

		final AlignmentService.RequestCustomizer sut = DependencyExclusionCustomizer.fromConfigurationForModule(configuration, PROJECT);

		assertThat(sut).isSameAs(AlignmentService.RequestCustomizer.NOOP);
	}

	@Test
	public void erroneousPropertiesDontCauseFailure() {
		final Map<String, String> properties = new HashMap<String, String>() {{
			put("dependencyExclusion.org.acme", "");
		}};
		final Configuration configuration = new MapConfiguration(properties);

		final AlignmentService.RequestCustomizer sut = DependencyExclusionCustomizer.fromConfigurationForModule(configuration, PROJECT);

		assertThat(sut).isSameAs(AlignmentService.RequestCustomizer.NOOP);
	}

	@Test
	public void ensureExclusionMatches() {
		final ProjectVersionRef project = AlignmentUtils.withGAV("org.acme", "dummy", "1.0.0");
		final ProjectVersionRef hibernateGav = AlignmentUtils.withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
		final ProjectVersionRef hibernateValidatorGav = AlignmentUtils.withGAV("org.hibernate", "hibernate-validator", "6.0.16.Final");
		final ProjectVersionRef undertowGav = AlignmentUtils.withGAV("io.undertow", "undertow-core", "2.0.15.Final");
		final ProjectVersionRef jacksonDatabindGav = AlignmentUtils.withGAV("com.fasterxml.jackson.core", "jackson-databind", "2.9.8");
		final ProjectVersionRef mongoGav = AlignmentUtils.withGAV("org.mongodb", "mongo-java-driver", "3.10.2");
		final ProjectVersionRef mockitoGav = AlignmentUtils.withGAV("org.mockito", "mockito-core", "2.27.0");
		final ProjectVersionRef wiremockGav = AlignmentUtils.withGAV("com.github.tomakehurst", "wiremock-jre8", "2.23.2");

		final Map<String, String> properties = new HashMap<String, String>() {{
			put("dependencyExclusion.org.hibernate:*@*", "");  // should result in the removal of all our hibernate deps
			put("dependencyExclusion.com.fasterxml.jackson.core:jackson-databind@*", ""); // should result in the removal of our jackson dependency
			put("dependencyExclusion.io.undertow:undertow-servlet@*", ""); // should NOT result in the removal of our undertow dependency since the artifact doesn't match
			put("dependencyExclusion.org.mockito:*@org.acme:test", ""); // should result in the removal of our mockito dependency
			put("dependencyExclusion.com.github.tomakehurst:*@org.acme:other", ""); // should NOT result in the removal of our wiremock dependency since the module doesn't match
		}};
		final Configuration configuration = new MapConfiguration(properties);


		final AlignmentService.RequestCustomizer sut = DependencyExclusionCustomizer.fromConfigurationForModule(configuration, PROJECT);

		final AlignmentService.Request originalReq = new AlignmentService.Request(project,
				Arrays.asList(hibernateGav, hibernateValidatorGav, undertowGav, jacksonDatabindGav, mongoGav, mockitoGav, wiremockGav));
		final AlignmentService.Request customizedReq = sut.customize(originalReq);

		assertThat(customizedReq).isNotNull().satisfies(r -> {
			assertThat(r.getProject()).isEqualTo(project);
			assertThat(r.getDependencies()).extracting("artifactId").containsOnly("undertow-core", "mongo-java-driver",
					"wiremock-jre8");
		});
	}
}