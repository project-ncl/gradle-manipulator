package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

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
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File projectRoot = tempDir.newFolder("opentelemetry");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
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
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File projectRoot = tempDir.newFolder("opentelemetry-kotlin");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(),
                Collections.singletonMap("overrideTransitive", "false"));

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START_KOTLIN, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END_KOTLIN,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE + ".kts")));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("io.opentelemetry");
            assertThat(am.getName()).isEqualTo("opentelemetry-java");
            assertThat(am.getVersion()).isEqualTo("0.17.0.redhat-00001");

            assertThat(am.getChildren().keySet()).hasSize(4).containsExactly("bom", "api", "dependencyManagement", "exporters");

            assertThat(am.getChildren().get("bom").toString())
                    .isEqualTo("io.opentelemetry:opentelemetry-bom:0.17.0.redhat-00001");
            assertThat(am.getChildren().get("api").toString()).isEqualTo("io.opentelemetry:api:0.17.0.redhat-00001");
            assertThat(am.findCorrespondingChild("exporters").getChildren().get("jaeger").toString())
                    .isEqualTo("io.opentelemetry.exporters:opentelemetry-exporter-jaeger:0.17.0.redhat-00001");

            assertThat(am.findCorrespondingChild("bom")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("0.17.0.redhat-00001");
                assertThat(root.getAlignedDependencies()).isEmpty();
            });
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));
        assertThat(systemOutRule.getLog()).contains("io.opentelemetry.exporters:opentelemetry-exporter-jaeger:0.17.0");
        assertThat(systemOutRule.getLog()).contains("io.opentelemetry:opentelemetry-bom:0.17.0");
    }
}
