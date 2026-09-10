package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

/**
 * Functional tests for {@code enforceVersionPrefix}.
 *
 * <p>
 * Both tests use a minimal fixture ({@code group=org.acme.gradle}, {@code version=1.0.1},
 * {@code rootProject.name = 'root'}) with no dependencies. The DA mock is configured per-test.
 *
 * <p>
 * Assertions cover:
 * <ul>
 * <li>The {@code LOOKUP_LATEST} REST call carries the <em>normalised</em> version
 * ({@code 1.0.1.rhlw-00000}), not the raw Gradle-declared version ({@code 1.0.1}).</li>
 * <li>The resulting {@code manipulation.json} records the fully enforced version.</li>
 * </ul>
 */
public class EnforceVersionPrefixFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        // No dependencies in the fixture — always return an empty LOOKUP_GAVS response.
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody("[]")));
        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    /**
     * With {@code enforceVersionPrefix=rhlw} and no explicit incremental suffix, the normalised
     * version {@code 1.0.1.rhlw-00000} is sent to DA. DA returns {@code 1.0.1.rhlw-00000} as the
     * latest version; the calculator produces {@code 1.0.1.rhlw-00001}.
     */
    @Test
    public void enforceVersionPrefix_normalisedVersionSentToDA()
            throws IOException, URISyntaxException, ManipulationException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "enforce-version-prefix-da-response-project.json"))));

        final Map<String, String> alignProps = new HashMap<>();
        alignProps.put("scanProjectsWithNoPublications", "true");
        alignProps.put("enforceVersionPrefix", "rhlw");
        // Suppress the default 'redhat' incremental suffix so only rhlw enforcement applies.
        alignProps.put("versionIncrementalSuffix", "");

        final File projectRoot = tempDir.newFolder("enforce-version-prefix");
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                "enforce-version-prefix",
                alignProps);

        // The REST mock must have received the normalised version, not the raw "1.0.1".
        verify(
                1,
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1.rhlw-00000\""))
                        .withRequestBody(containing("\"artifactId\":\"root\"")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getOriginalVersion()).isEqualTo("1.0.1");
                assertThat(root.getVersion()).isEqualTo("1.0.1.rhlw-00001");
            });
        });
    }

    /**
     * With {@code enforceVersionPrefix=rhlw} and {@code versionIncrementalSuffix=n}, the normalised
     * version {@code 1.0.1.rhlw-00000} is sent to DA. DA returns {@code 1.0.1.rhlw-00000-n-00001}
     * as the latest candidate; the calculator produces {@code 1.0.1.rhlw-00000-n-00001}.
     */
    @Test
    public void enforceVersionPrefix_withIncrementalSuffix_normalisedVersionSentToDA()
            throws IOException, URISyntaxException, ManipulationException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "enforce-version-prefix-with-incremental-da-response-project.json"))));

        final Map<String, String> alignProps = new HashMap<>();
        alignProps.put("scanProjectsWithNoPublications", "true");
        alignProps.put("enforceVersionPrefix", "rhlw");
        alignProps.put("versionIncrementalSuffix", "n");

        final File projectRoot = tempDir.newFolder("enforce-version-prefix-incremental");
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                "enforce-version-prefix",
                alignProps);

        // The REST mock must have received the normalised version, not the raw "1.0.1".
        verify(
                1,
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1.rhlw-00000\""))
                        .withRequestBody(containing("\"artifactId\":\"root\"")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getOriginalVersion()).isEqualTo("1.0.1");
                assertThat(root.getVersion()).isEqualTo("1.0.1.rhlw-00000-n-00001");
            });
        });
    }
}
