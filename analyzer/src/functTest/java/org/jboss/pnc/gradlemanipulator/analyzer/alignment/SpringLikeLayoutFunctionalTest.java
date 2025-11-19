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
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
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

public class SpringLikeLayoutFunctionalTest extends AbstractWiremockTest {

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
        // XXX: Caused by: java.lang.ClassNotFoundException: org.gradle.api.artifacts.maven.PomFilterContainer
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        final File projectRoot = tempDir.newFolder("spring-like-layout");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(
                AlignmentTask.INJECT_GME_END,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.getVersion()).isEqualTo("1.1.2.redhat-00004");

            assertThat(am.getChildren().keySet()).hasSize(3)
                    .containsExactly("subproject1", "subproject2", "subproject3");

            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.1.2.redhat-00004");
                assertThat(root.getAlignedDependencies()).isEmpty();
            });

            assertThat(am.findCorrespondingChild("subproject1")).satisfies(subproject1 -> {
                assertThat(subproject1.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject1.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("resteasy-jaxrs", "3.6.3.SP1-redhat-00001"));
            });

            assertThat(am.findCorrespondingChild("subproject2")).satisfies(subproject2 -> {
                assertThat(subproject2.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject2.getAlignedDependencies().values();
                assertThat(alignedDependencies).isEmpty();
            });

            assertThat(am.findCorrespondingChild("subproject3")).satisfies(subproject11 -> {
                assertThat(subproject11.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject11.getAlignedDependencies()
                        .values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"));
            });
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));

        File pluginConfigs = new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS);
        String pContents = org.apache.commons.io.FileUtils.readFileToString(pluginConfigs, Charset.defaultCharset());
        assertTrue(systemOutRule.getLinesNormalized().contains("Replacing Dokka template for version MINIMUM"));
        assertTrue(pContents.contains("noJdkLink = true"));

        File settings = new File(projectRoot, "settings.gradle");
        String sContents = org.apache.commons.io.FileUtils.readFileToString(settings, Charset.defaultCharset());
        assertTrue(systemOutRule.getLinesNormalized().contains("with Dokka resolutionStrategy information"));
        assertTrue(sContents.contains("pluginManagement { resolutionStrategy { eachPlugin { if (requested.id.id =="));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRunNoDokka() throws IOException, URISyntaxException,
            ManipulationException {
        // XXX: Caused by: java.lang.ClassNotFoundException: org.gradle.api.artifacts.maven.PomFilterContainer
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("7.0")) < 0);

        final File projectRoot = tempDir.newFolder("spring-like-layout");
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                projectRoot.getName(),
                Collections.singletonMap("dokkaPlugin", "false"));

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(
                AlignmentTask.INJECT_GME_END,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.getVersion()).isEqualTo("1.1.2.redhat-00004");

            assertThat(am.getChildren().keySet()).hasSize(3)
                    .containsExactly("subproject1", "subproject2", "subproject3");

            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.1.2.redhat-00004");
                assertThat(root.getAlignedDependencies()).isEmpty();
            });

            assertThat(am.findCorrespondingChild("subproject1")).satisfies(subproject1 -> {
                assertThat(subproject1.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject1.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("resteasy-jaxrs", "3.6.3.SP1-redhat-00001"));
            });

            assertThat(am.findCorrespondingChild("subproject2")).satisfies(subproject2 -> {
                assertThat(subproject2.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject2.getAlignedDependencies().values();
                assertThat(alignedDependencies).isEmpty();
            });

            assertThat(am.findCorrespondingChild("subproject3")).satisfies(subproject11 -> {
                assertThat(subproject11.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject11.getAlignedDependencies()
                        .values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"));
            });
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));

        File pluginConfigs = new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS);
        String pContents = org.apache.commons.io.FileUtils.readFileToString(pluginConfigs, Charset.defaultCharset());
        assertFalse(systemOutRule.getLinesNormalized().contains("Replacing Dokka template for version MINIMUM"));
        assertFalse(pContents.contains("noJdkLink = true"));

        File settings = new File(projectRoot, "settings.gradle");
        String sContents = org.apache.commons.io.FileUtils.readFileToString(settings, Charset.defaultCharset());
        assertFalse(sContents.contains("pluginManagement { resolutionStrategy { eachPlugin { if (requested.id.id =="));
    }
}
