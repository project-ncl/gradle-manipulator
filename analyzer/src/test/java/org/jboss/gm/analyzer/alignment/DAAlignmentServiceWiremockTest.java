package org.jboss.gm.analyzer.alignment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

public class DAAlignmentServiceWiremockTest {

	private static final int PORT = 8089;

	@Rule
	public WireMockRule wireMockRule = new WireMockRule(PORT);

	@Before
	public void setup() {
		stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json;charset=utf-8")
						.withBody(readSampleDAResponse())));
	}

	@Test
	public void alignmentWorksAsExpected() {
		final DAAlignmentService sut = new DAAlignmentService(String.format("http://localhost:%d/da/rest/v-1", PORT));

		final GAV.Simple projectGav = new GAV.Simple("org.acme", "dummy", "1.0.0");
		final GAV.Simple hibernateGav = new GAV.Simple("org.hibernate", "hibernate-core", "5.3.7.Final");
		final GAV.Simple undertowGav = new GAV.Simple("io.undertow", "undertow-core", "2.0.15.Final");
		final GAV.Simple mockitoGav = new GAV.Simple("org.mockito", "mockito-core", "2.27.0");
		final AlignmentService.Response response = sut.align(new AlignmentService.Request(
				projectGav,
				Arrays.asList(
						hibernateGav,
						undertowGav,
						mockitoGav
				)));

		assertThat(response).isNotNull().satisfies(r -> {
			assertThat(r.getNewProjectVersion()).isNull();
			assertThat(r.getAlignedVersionOfGav(hibernateGav)).isEqualTo("5.3.7.Final-redhat-00001");
			assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo("2.0.15.Final-redhat-00001");
			assertThat(r.getAlignedVersionOfGav(mockitoGav)).isNull();
		});
	}


	private String readSampleDAResponse() {
		try {
			return FileUtils.readFileToString(
					Paths.get(DAAlignmentServiceWiremockTest.class.getClassLoader().getResource("sample-da-response.json").toURI()).toFile(), StandardCharsets.UTF_8.name());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}