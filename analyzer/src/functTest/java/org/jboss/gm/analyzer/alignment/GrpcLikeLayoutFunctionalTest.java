package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang.reflect.FieldUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.model.ManipulationModel;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;

public class GrpcLikeLayoutFunctionalTest extends AbstractWiremockTest {

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
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .inScenario("multi-module")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("spring-like-layout-da-" + "root" + ".json")))
                .willSetStateTo("project root called"));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun() throws IOException, URISyntaxException, ManipulationException {

        final File projectRoot = tempDir.newFolder("grpc-like-layout");
        final Map<String, String> props = Collections.singletonMap("dependencyExclusion.io.netty:*@*",
                "4.1.42.Final-redhat-00001");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), props);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.getVersion()).isEqualTo("1.1.2.redhat-00004");

            assertThat(am.getChildren().keySet()).hasSize(3).containsExactly("subproject1", "subproject2",
                    "subproject3");

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

            assertThat(am.findCorrespondingChild("subproject3")).satisfies(subproject3 -> {
                assertThat(subproject3.getName()).isEqualTo("special-subproject-number3");
                assertThat(subproject3.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject3.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"));
            });
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/reports/lookup/gavs")));
    }

    @Test
    public void ensureMissingGroupIdThrowsException() throws IOException, URISyntaxException {

        final File projectRoot = tempDir.newFolder("grpc-like-layout");

        //noinspection ConstantConditions
        org.apache.commons.io.FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);
        // Remove the settings so we can't use a child project to establish groupId
        //noinspection ResultOfMethodCallIgnored
        new File(projectRoot, "settings.gradle").delete();
        // Write a version so this error isn't found first.
        org.apache.commons.io.FileUtils.writeStringToFile(
                new File(projectRoot, "gradle.properties"), "    version = \"1.1.2\"\n", Charset.defaultCharset());

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> TestUtils.align(projectRoot, true))
                .withMessageContaining("Empty groupId but unable to determine a suitable replacement from any child modules");
    }

    @Test
    public void ensureMissingVersionThrowsException() throws IOException, URISyntaxException {

        final File projectRoot = tempDir.newFolder("grpc-like-layout");

        //noinspection ConstantConditions
        org.apache.commons.io.FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);
        // Remove the settings so we can't use a child project to establish version
        //noinspection ResultOfMethodCallIgnored
        new File(projectRoot, "settings.gradle").delete();

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> TestUtils.align(projectRoot, true))
                .withMessageContaining("Unable to find suitable project version");
    }

    @Test
    public void ensureAlignmentFileWithArchiveBaseNameOverride() throws IOException, URISyntaxException, ManipulationException {

        final File projectRoot = tempDir.newFolder("grpc-like-layout");
        final Map<String, String> props = Collections.singletonMap("dependencySource", "NONE");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), props);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");

            assertThat(am.getChildren().keySet()).hasSize(3).containsExactly("subproject1", "subproject2",
                    "subproject3");
            assertThat(am.findCorrespondingChild("subproject3")).satisfies(subproject3 -> {
                assertThat(subproject3.getName().equals("special-subproject-number3"));
                try {
                    assertEquals(FieldUtils.getDeclaredField(ManipulationModel.class, "projectPathName", true).get(subproject3),
                            "subproject3");
                } catch (IllegalAccessException e) {
                    fail("Couldn't get field to check.");
                }
                assertThat(subproject3.getChildren().keySet()).hasSize(1).containsExactly("subsubproject1");
            });
        });
    }
}
