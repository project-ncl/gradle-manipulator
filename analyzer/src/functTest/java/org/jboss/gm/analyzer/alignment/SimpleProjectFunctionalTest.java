package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SimpleProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() {
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-da-response.json"))));
    }

    @Test
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException {
        final ManipulationModel alignmentModel = TestUtils.align(tempDir, "simple-project");

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00001");
                assertThat(root.getName()).isEqualTo("root");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("undertow-core", "2.0.15.Final-redhat-00001"),
                                tuple("hibernate-core", "5.3.7.Final-redhat-00001"));
            });
        });
    }
}
