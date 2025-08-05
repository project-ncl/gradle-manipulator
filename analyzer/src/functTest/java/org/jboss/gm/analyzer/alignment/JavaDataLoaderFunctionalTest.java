package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.gradle.api.Project;
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

public class JavaDataLoaderFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

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
                                        .withBody(readSampleDAResponse("simple-project-da-response.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("simple-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("java-dataloader-like-project");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                projectRoot.getName(),
                Collections.singletonMap("dependencyOverride.com.yammer.metrics:*@org.acme.gradle:root", ""));

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + '/' + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();
        assertThat(TestUtils.getLine(projectRoot)).isEqualTo(AlignmentTask.INJECT_GME_START + " }");
        assertThat(FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)))
                .isEqualTo(AlignmentTask.INJECT_GME_END);
        assertThat(systemOutRule.getLog())
                .contains("Passing 1 project GAVs into the REST client api [com.graphql-java:java-dataloader:");

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("com.graphql-java");
            assertThat(am.getName()).isEqualTo("java-dataloader");
        });
    }
}
