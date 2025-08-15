package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class SemanticConventionsFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "semantic-conventions-da-response-project.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody("[]")));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void verifySemanticConventions() throws IOException, URISyntaxException, ManipulationException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.2")) >= 0);

        final File projectRoot = tempDir.newFolder("semantic-conventions-java");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                projectRoot.getName());

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + File.separator + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("1.26.0-alpha");
            assertThat(am.getGroup()).isEqualTo("io.opentelemetry.semconv");
            assertThat(am.getName()).isEqualTo("semantic-conventions-java");
            assertThat(am.findCorrespondingChild("semconv-incubating")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.26.0.alpha-redhat-00002");
                assertThat(root.getName()).isEqualTo("opentelemetry-semconv-incubating");
            });
        });
    }
}
