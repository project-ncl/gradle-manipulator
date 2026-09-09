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
 * Functional tests for rebuilding a project when a suffix is already present — either in the
 * source code ({@code gradle.properties}), in an existing {@code manipulation.json}, or in both.
 *
 * <p>
 * All three cases use the Lightwell suffix convention: {@code rhlw} as the first/existing
 * suffix and {@code n} as the new incremental suffix added by the rebuild. DA always returns
 * {@code 1.0.1.rhlw-00001-n-00001} in response to a request carrying {@code 1.0.1.rhlw-00001}.
 */
public class RebuildWithDifferentSuffixFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    /** System properties shared by every test in this class. */
    private java.util.Map<String, String> alignProps() {
        java.util.Map<String, String> props = new java.util.HashMap<>();
        props.put("scanProjectsWithNoPublications", "true");
        // Use the 'n' incremental suffix so the calculator produces <base>-n-00001,
        // overriding the default 'redhat' suffix configured in Configuration.
        props.put("versionIncrementalSuffix", "n");
        return props;
    }

    @Before
    public void setup() throws IOException, URISyntaxException {
        // All tests share a no-op LOOKUP_GAVS response — the fixtures have no dependencies.
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
     * Case A: the Gradle-declared version already carries the {@code rhlw} suffix; no
     * {@code manipulation.json} exists. The suffixed Gradle version must be sent verbatim to
     * {@code LOOKUP_LATEST} and DA adds the {@code n} suffix on top.
     *
     * <p>
     * Expected result: {@code 1.0.1.rhlw-00001-n-00001}
     */
    @Test
    public void rebuildWhenCodeCarriesSuffix_noExistingManipulationFile()
            throws IOException, URISyntaxException, ManipulationException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "rebuild-suffix-in-code-da-response-project.json"))));

        final File projectRoot = tempDir.newFolder("rebuild-with-suffix-in-code");
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                "rebuild-with-suffix-in-code",
                alignProps());

        // No manipulation.json was present, so the Gradle version is sent directly.
        verify(
                1,
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1.rhlw-00001\""))
                        .withRequestBody(containing("\"artifactId\":\"root\"")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.rhlw-00001-n-00001");
                assertThat(root.getOriginalVersion()).isEqualTo("1.0.1.rhlw-00001");
            });
        });
    }

    /**
     * Case B: {@code gradle.properties} has a clean version; {@code manipulation.json} carries the
     * {@code rhlw}-suffixed version from a previous build. The file version must be sent to
     * {@code LOOKUP_LATEST} and also used as the {@code VersionCalculator} input, so the new
     * suffix is appended on top of the full prior version.
     *
     * <p>
     * Expected result: {@code 1.0.1.rhlw-00001-n-00001}
     */
    @Test
    public void rebuildWhenManipulationFileCarriesSuffix_codeIsClean()
            throws IOException, URISyntaxException, ManipulationException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "rebuild-suffix-in-manipulation-file-da-response-project.json"))));

        final File projectRoot = tempDir.newFolder("rebuild-with-suffix-in-manipulation-file");
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                "rebuild-with-suffix-in-manipulation-file",
                alignProps());

        // The manipulation.json version (rhlw-00001) must be sent to DA, not the raw "1.0.1".
        verify(
                1,
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1.rhlw-00001\""))
                        .withRequestBody(containing("\"artifactId\":\"root\"")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.rhlw-00001-n-00001");
                // originalVersion must reflect the Gradle-declared value, not the file version.
                assertThat(root.getOriginalVersion()).isEqualTo("1.0.1");
            });
        });
    }

    /**
     * Case C: both {@code gradle.properties} and {@code manipulation.json} carry the same
     * {@code rhlw}-suffixed version (a re-run without a fresh checkout). Produces the same result
     * as Case B since the cache PVR version and the file version are identical.
     *
     * <p>
     * Expected result: {@code 1.0.1.rhlw-00001-n-00001}
     */
    @Test
    public void rebuildWhenBothCodeAndManipulationFileCarrySuffix()
            throws IOException, URISyntaxException, ManipulationException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "rebuild-suffix-in-manipulation-file-da-response-project.json"))));

        final File projectRoot = tempDir.newFolder("rebuild-with-suffix-in-both");
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                "rebuild-with-suffix-in-both",
                alignProps());

        verify(
                1,
                postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .withRequestBody(containing("\"version\":\"1.0.1.rhlw-00001\""))
                        .withRequestBody(containing("\"artifactId\":\"root\"")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.rhlw-00001-n-00001");
                assertThat(root.getOriginalVersion()).isEqualTo("1.0.1.rhlw-00001");
            });
        });
    }
}
