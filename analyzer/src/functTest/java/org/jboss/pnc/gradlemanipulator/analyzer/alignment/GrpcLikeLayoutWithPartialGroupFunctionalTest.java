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
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.utils.FileUtils;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class GrpcLikeLayoutWithPartialGroupFunctionalTest
        extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

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
                                        .withBody(readSampleDAResponse("spring-like-layout-da-root.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("spring-like-layout-da-root-project.json"))));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun()
            throws IOException, URISyntaxException, ManipulationException {
        // XXX: Spring plugins uses org/gradle/api/tasks/Upload
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("9.0.0")) < 0);

        final File projectRoot = tempDir.newFolder("grpc-like-layout-with-partial-group");
        final Map<String, String> props = Collections.singletonMap(
                "dependencyOverride.io.netty:*@*",
                "4.1.42.Final-redhat-00001");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), props);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(
                AlignmentTask.INJECT_GME_END,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("mongo-java-driver-reactivestreams");
            assertThat(am.getVersion()).isEqualTo("1.1.2.redhat-00004");

            assertThat(am.getChildren().keySet()).hasSize(2).containsExactly("subproject1", "subproject2");

            assertThat(am.findCorrespondingChild("subproject1")).satisfies(subproject1 -> {
                assertThat(subproject1.getVersion()).isEqualTo("1.1.2.redhat-00004");
                assertThat(subproject1.getGroup()).isEqualTo("org.acme");
            });

            assertThat(am.findCorrespondingChild("subproject2")).satisfies(subproject2 -> {
                assertThat(subproject2.getVersion()).isEqualTo("1.1.2.redhat-00004");
                assertThat(subproject2.getGroup()).isNull();
                final Collection<ProjectVersionRef> alignedDependencies = subproject2.getAlignedDependencies().values();
                assertThat(alignedDependencies).isEmpty();
            });

        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));
    }
}
