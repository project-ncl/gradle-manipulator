package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
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
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;

public class VersionConflictProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-da-response.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void validateResolutionStrategy() throws IOException, URISyntaxException, ManipulationException {
        // XXX: Use of pluginManagement.plugins{}
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.6")) >= 0);
        // XXX: Caused by: org.gradle.api.InvalidUserDataException: You can't map a property that does not exist: propertyName=classifier
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0);

        final File projectRoot = tempDir.newFolder("version-conflict");

        final Map<String, String> map = new HashMap<>();
        map.put("overrideTransitive", "false");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), map);

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(TestUtils.getLine(projectRoot)).isEqualTo(AlignmentTask.INJECT_GME_START + " }");
        assertThat(FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)))
                .isEqualTo(AlignmentTask.INJECT_GME_END);

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
                assertThat(root.getName()).isEqualTo("root");

                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("undertow-core", "2.0.15.Final-redhat-00001"),
                                tuple("hibernate-core", "5.3.7.Final-redhat-00001"));
            });
        });

        assertThat(systemOutRule.getLog()).contains("Detected use of conflict resolution strategy strict");
    }

    @Test
    public void validateTransitiveDisabledAndShadow() throws IOException, URISyntaxException {
        // XXX: Use of pluginManagement.plugins{}
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.6")) >= 0);
        // XXX: Caused by: org.gradle.api.InvalidUserDataException: You can't map a property that does not exist: propertyName=classifier
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0);

        final File projectRoot = tempDir.newFolder("version-conflict");

        final Map<String, String> map = new HashMap<>();
        try {
            TestUtils.align(projectRoot, projectRoot.getName(), true, map);
        } catch (ManipulationUncheckedException e) {
            assertThat(e.getMessage()).contains(
                    "Shadow plugin (for shading) configured but overrideTransitive has not been explicitly enabled or disabled");
        }

    }

    @Test
    public void validateTransitiveEnabledAndShadow() throws IOException, URISyntaxException, ManipulationException {
        // XXX: Use of pluginManagement.plugins{}
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.6")) >= 0);
        // XXX: Caused by: org.gradle.api.InvalidUserDataException: You can't map a property that does not exist: propertyName=classifier
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0);

        final File projectRoot = tempDir.newFolder("version-conflict");

        final Map<String, String> map = new HashMap<>();
        map.put("overrideTransitive", "true");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), map);

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(TestUtils.getLine(projectRoot)).isEqualTo(AlignmentTask.INJECT_GME_START + " }");
        assertThat(FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)))
                .isEqualTo(AlignmentTask.INJECT_GME_END);

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
                assertThat(root.getName()).isEqualTo("root");

                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("antlr", "2.7.7.redhat-00001"),
                                tuple("undertow-core", "2.0.15.Final-redhat-00001"),
                                tuple("hibernate-core", "5.3.7.Final-redhat-00001"));
            });
        });

        assertThat(systemOutRule.getLog()).contains("Detected use of conflict resolution strategy strict");
        assertThat(systemOutRule.getLog()).contains("Passing 18 GAVs into the REST client api [");
    }
}
