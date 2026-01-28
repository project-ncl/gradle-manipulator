package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class CandlepinProjectFunctionalTest extends AbstractWiremockTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("candlepin-project-da-response.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("candlepin-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException, ManipulationException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.3")) >= 0);

        final File projectRoot = tempDir.newFolder("candlepin-project");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                projectRoot.getName());

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + File.separator + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
        assertEquals(
                AlignmentTask.INJECT_GME_END,
                org.jboss.pnc.gradlemanipulator.common.utils.FileUtils
                        .getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("4.7.3");
            assertThat(am.getGroup()).isEqualTo("org.candlepin");
            assertThat(am.getName()).isEqualTo("candlepin");
            assertThat(am.getVersion()).isEqualTo("4.7.3.redhat-00002");
            final Collection<ProjectVersionRef> alignedDependencies = am.getAlignedDependencies().values();
            assertThat(alignedDependencies)
                    .extracting("artifactId", "versionString")
                    .containsOnly(
                            tuple("commons-codec", "1.20.0.redhat-00001"),
                            tuple("javax.annotation-api", "1.3.2.redhat-00003"));
            assertThat(am.findCorrespondingChild("client")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("4.7.3.redhat-00002");
                assertThat(root.getName()).isEqualTo("client");
                final Collection<ProjectVersionRef> childAlignedDependencies = root.getAlignedDependencies().values();
                assertThat(childAlignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("javax.annotation-api", "1.3.2.redhat-00003"));
            });
        });
    }
}
