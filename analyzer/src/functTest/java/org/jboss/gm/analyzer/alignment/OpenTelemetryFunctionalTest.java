package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.utils.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;

public class OpenTelemetryFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubDACall();

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    private void stubDACall() throws IOException, URISyntaxException {
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("spring-like-layout-da-root.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("spring-like-layout-da-root-project.json"))));
    }

    @Test
    public void verifyOpenTelemetryGradle() throws IOException, URISyntaxException, ManipulationException {
        // XXX: Use of pluginManagement.plugins{}
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.6")) >= 0);
        // XXX:  Caused by: org.gradle.api.GradleException: Dependencies can not be declared against the `compileClasspath` configuration.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0);

        final File projectRoot = tempDir.newFolder("opentelemetry");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("io.opentelemetry");
            assertThat(am.getName()).isEqualTo("opentelemetry-java");
            assertThat(am.getVersion()).isEqualTo("0.6.0.redhat-00001");

            assertThat(am.getChildren().keySet()).hasSize(2).containsExactly("opentelemetry-api", "opentelemetry-bom");

            assertThat(am.findCorrespondingChild("opentelemetry-bom")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("0.6.0.redhat-00001");
                assertThat(root.getAlignedDependencies()).isEmpty();
            });
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));
    }

    @Test
    public void verifyOpenTelemetryKotlin() throws IOException, URISyntaxException, ManipulationException {
        // XXX: Use of pluginManagement.plugins{}
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.6")) >= 0);
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.5")) < 0);

        final File projectRoot = tempDir.newFolder("opentelemetry-kotlin");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(),
                Collections.singletonMap("overrideTransitive", "false"));

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START_KOTLIN + " }", TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END_KOTLIN,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE + ".kts")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("io.opentelemetry");
            assertThat(am.getName()).isEqualTo("opentelemetry-java");
            assertThat(am.getVersion()).isEqualTo("0.17.0.redhat-00001");

            assertThat(am.getChildren().keySet()).hasSize(4).containsExactly("bom", "api", "dependencyManagement", "exporters");

            assertThat(am.getChildren().get("bom"))
                    .hasToString("io.opentelemetry:bom:0.17.0.redhat-00001");
            assertThat(am.getChildren().get("api")).hasToString("io.opentelemetry:api:0.17.0.redhat-00001");
            assertThat(am.findCorrespondingChild("exporters").getChildren().get("jaeger"))
                    .hasToString("io.opentelemetry.exporters:jaeger:0.17.0.redhat-00001");

            assertThat(am.findCorrespondingChild("bom")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("0.17.0.redhat-00001");
                assertThat(root.getAlignedDependencies()).isEmpty();
            });
            assertThat(am.findCorrespondingChild("dependencyManagement")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("0.17.0.redhat-00001");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("protobuf-bom", "3.14.0-redhat-00001"));
            });
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));
        assertThat(systemOutRule.getLog()).contains("io.opentelemetry.exporters:opentelemetry-exporter-jaeger:0.17.0");
        assertThat(systemOutRule.getLog()).contains("io.opentelemetry:bom:0.17.0");
    }

    @Test
    public void verifyOpenTelemetryJavaInstrumentationKotlin() throws Exception {
        // XXX: Kotlin requirements
        System.out.println("### " + GradleVersion.current());
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.5")) >= 0);
        // XXX:  Caused by: org.gradle.api.GradleException: Dependencies can not be declared against the `compileClasspath` configuration.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0);

        final Map<String, String> map = new HashMap<>();
        map.put("-DoverrideTransitive", "false");
        map.put("-Potel.stable", "true");
        map.put("-DignoreUnresolvableDependencies", "true");
        map.put("-DpluginRemoval", "gradle-enterprise,io.github.gradle-nexus.publish-plugin");

        final File projectRoot = tempDir.newFolder("opentelemetry-kotlin-2");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), map);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START_KOTLIN + " }", TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END_KOTLIN,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE + ".kts")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            // In the full build its io.opentelemetry due to multiple modules but this test only has a subset.
            assertThat(am.getGroup()).isEqualTo("io.opentelemetry.instrumentation");
            assertThat(am.getName()).isEqualTo("opentelemetry-java-instrumentation");
            assertThat(am.getVersion()).isEqualTo("1.17.0.redhat-00001");

            assertThat(am.getChildren().keySet()).hasSize(7).contains(
                    "bom-alpha",
                    "benchmark-overhead-jmh",
                    "custom-checks",
                    "dependencyManagement",
                    "instrumentation-api",
                    "instrumentation-api-semconv",
                    "smoke-tests");

            assertThat(am.getChildren().get("bom-alpha"))
                    .hasToString("io.opentelemetry.instrumentation:bom-alpha:1.17.0.redhat-00001");
            assertThat(am.findCorrespondingChild("bom-alpha")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.17.0.redhat-00001");
                assertThat(root.getAlignedDependencies()).isEmpty();
            });
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));
        assertThat(systemOutRule.getLog()).contains("Found archivesBaseName override ; resetting project name "
                + "'benchmark-overhead-jmh' to 'opentelemetry-benchmark-overhead-jmh'");
    }
}
