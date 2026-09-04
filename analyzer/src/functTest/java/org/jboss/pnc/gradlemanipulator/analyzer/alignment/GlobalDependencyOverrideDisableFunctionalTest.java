package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.utils.FileUtils;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

/**
 * Functional regression test for the global dependency-alignment disable:
 * {@code dependencyOverride.*:*@*=} must suppress DA dependency alignments in every module,
 * including ungrouped modules such as Ehcache utility/test sub-projects.
 */
public class GlobalDependencyOverrideDisableFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public final TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubDACall();
        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    private void stubDACall() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "global-dep-override-disable-da-root.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "global-dep-override-disable-da-root-project.json"))));
    }

    /**
     * Verifies that {@code dependencyOverride.*:*@*=} produces empty {@code alignedDependencies}
     * in every module — root, grouped sub-project, and ungrouped sub-project — while project
     * version changes still occur.
     */
    @Test
    public void globalEmptyOverrideSuppressesDependencyAlignmentInAllModules()
            throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("global-dep-override-disable");
        new File(projectRoot, "grouped-sub").mkdirs();
        new File(projectRoot, "ungrouped-sub").mkdirs();

        final Map<String, String> alignProps = new LinkedHashMap<>();
        alignProps.put("scanProjectsWithNoPublications", "true");
        // Global empty override: suppress all dependency alignments in all modules.
        alignProps.put("dependencyOverride.*:*@*", "");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                "global-dep-override-disable",
                alignProps);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
        assertEquals(
                AlignmentTask.INJECT_GME_END,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            // Root module: project version was aligned; no dependency alignments.
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.getVersion()).startsWith("1.0.0");
            assertThat(am.getVersion()).contains("redhat");

            final Collection<ProjectVersionRef> rootAligned = am.getAlignedDependencies().values();
            assertThat(rootAligned)
                    .as("root module must have no aligned dependencies when global override is active")
                    .isEmpty();

            assertThat(am.getChildren().keySet()).containsExactlyInAnyOrder("grouped-sub", "ungrouped-sub");

            // grouped-sub: has a group; must still have no aligned dependencies.
            assertThat(am.findCorrespondingChild("grouped-sub")).satisfies(groupedSub -> {
                assertThat(groupedSub.getVersion()).startsWith("1.0.0");
                final Collection<ProjectVersionRef> aligned = groupedSub.getAlignedDependencies().values();
                assertThat(aligned)
                        .as("grouped-sub must have no aligned dependencies when global override is active")
                        .isEmpty();
            });

            // ungrouped-sub: has no group; the @* rule must also suppress its dependency alignments.
            assertThat(am.findCorrespondingChild("ungrouped-sub")).satisfies(ungroupedSub -> {
                final Collection<ProjectVersionRef> aligned = ungroupedSub.getAlignedDependencies().values();
                assertThat(aligned)
                        .as("ungrouped-sub must have no aligned dependencies when global override is active")
                        .isEmpty();
            });
        });

        // Exactly one call to DA should still be made (dependencies are sent; overrides are post-REST).
        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));
    }
}
