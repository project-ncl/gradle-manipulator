package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationUncheckedException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

/**
 * Functional tests for the three fallback / error paths in the project-GAV reuse logic:
 *
 * <ul>
 * <li>Blank version in the existing model node → warning, Gradle version used in REST request.</li>
 * <li>GA mismatch between existing model node and current build → build fails with a focused error.</li>
 * <li>Missing model node for the current project path → warning, Gradle version used in REST request.</li>
 * </ul>
 */
public class ExistingManipulationFallbackFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        // Dependency response — none of these fixtures declare dependencies, so LOOKUP_GAVS is never called.
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody("[]")));
        // Project-version response — all fallback cases send the Gradle version "1.0.1".
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "existing-manipulation-fallback-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    /**
     * When the existing {@code manipulation.json} has a blank version for the project node, the build must not fail.
     * The current Gradle-declared version ("1.0.1") must be sent to {@code LOOKUP_LATEST} instead.
     */
    @Test
    public void blankVersionInExistingModelFallsBackToGradleVersion()
            throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("existing-manipulation-blank-version");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), false);

        // The Gradle-declared version must be sent, not the blank model version.
        verify(
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1\""))
                        .withRequestBody(containing("root")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getGroup()).isEqualTo("org.acme.gradle");
                assertThat(root.getName()).isEqualTo("root");
                // Version comes from DA response, not the blank model version.
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
                assertThat(root.getOriginalVersion()).isEqualTo("1.0.1");
            });
        });
    }

    /**
     * When the existing {@code manipulation.json} has a different group for the node matching the current project
     * name (GA mismatch), the build must fail with a clear error identifying both GA values.
     */
    @Test
    public void gaMismatchInExistingModelFailsBuild() throws IOException, URISyntaxException {
        final File projectRoot = tempDir.newFolder("existing-manipulation-ga-mismatch");

        assertThatThrownBy(() -> TestUtils.align(projectRoot, projectRoot.getName(), true))
                .isInstanceOf(ManipulationUncheckedException.class)
                .hasMessageContaining("org.different.group")
                .hasMessageContaining("org.acme.gradle");
    }

    /**
     * When the existing {@code manipulation.json} has no node for a newly added subproject, the build must not
     * fail. The known root sends its prior version; the unknown subproject sends the Gradle-declared version.
     */
    @Test
    public void missingNodeInExistingModelFallsBackToGradleVersion()
            throws IOException, URISyntaxException, ManipulationException {
        // Override the LOOKUP_LATEST stub to return entries for both root and new-child.
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "existing-manipulation-missing-node-da-response-project.json"))));

        final File projectRoot = tempDir.newFolder("existing-manipulation-missing-node");
        TestUtils.align(projectRoot, projectRoot.getName(), false);

        // Root uses its prior manipulated version; new-child (absent from the old model) uses the Gradle version.
        verify(
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1.redhat-00001\""))
                        .withRequestBody(containing("\"artifactId\":\"root\"")));
        verify(
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1\""))
                        .withRequestBody(containing("\"artifactId\":\"new-child\"")));
    }
}
