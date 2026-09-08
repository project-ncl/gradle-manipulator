package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Functional test verifying that projects using the legacy {@code maven} plugin
 * ({@code apply plugin: 'maven'} / {@code uploadArchives}) are aligned correctly
 * without requiring {@code scanProjectsWithNoPublications=true}.
 * <p>
 * Regression test for the "is defined but no publication ; skipping" issue caused by
 * {@code AlignmentTask} only recognising {@code maven-publish} {@code MavenPublication}
 * objects and silently skipping projects that publish via the legacy mechanism.
 * <p>
 * The legacy {@code maven} plugin was removed in Gradle 7.0; this test is skipped on
 * Gradle 7.0 and above.
 */
public class LegacyMavenPluginProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws URISyntaxException, IOException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "legacy-maven-plugin-project-da-response.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "legacy-maven-plugin-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    /**
     * Verifies that a project using {@code apply plugin: 'maven'} (legacy {@code uploadArchives}
     * publishing) is fully aligned without needing {@code scanProjectsWithNoPublications=true}.
     * The absence of that flag is the key assertion: before the fix this test would have produced
     * an empty / skipped alignment model.
     */
    @Test
    public void legacyMavenPluginProjectIsAligned()
            throws IOException, URISyntaxException, ManipulationException {

        // The legacy 'maven' plugin was removed in Gradle 7.0.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        final File projectRoot = tempDir.newFolder("legacy-maven-plugin-project");

        // No scanProjectsWithNoPublications=true — that is the whole point of this test.
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                projectRoot.getName(),
                Collections.emptyMap());

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("com.example.legacy");
            assertThat(am.getName()).isEqualTo("legacy-maven-plugin-project");
            assertThat(am.getOriginalVersion()).isEqualTo("1.0.0");
            assertThat(am.findCorrespondingChild("legacy-maven-plugin-project")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.0.redhat-00002");
                assertThat(root.getAlignedDependencies().values())
                        .extracting("artifactId", "versionString")
                        .containsOnly(tuple("commons-lang3", "3.8.1.redhat-00001"));
            });
        });
    }
}
