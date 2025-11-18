package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import kong.unirest.ContentType;
import kong.unirest.HeaderNames;
import kong.unirest.HttpStatus;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.api.Project;
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

public class WarFunctionalTest extends AbstractWiremockTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final String TEST_URL = "/da/rest/v-1/";

    private static final String PROJECT_NAME = "war-project";

    private static final String RESPONSE_FILE_NAME = PROJECT_NAME + "-da-response.json";

    private static final String RESPONSE_PROJECT_FILE_NAME = PROJECT_NAME + "-da-response-project.json";

    private static final String DA_HOST = "http://127.0.0.1";

    private static final String GROUP_ID = "org.jboss.spec.javax.servlet";

    private static final String ARTIFACT_ID = "jboss-servlet-api_2.5_spec";

    private static final String VERSION = "1.0.1.Final";

    private static final String VERSION_NEW = VERSION + "-redhat-3";

    private static final String GAV = String.join(":", GROUP_ID, ARTIFACT_ID, VERSION);

    private static final String GAV_NEW = String.join(":", GROUP_ID, ARTIFACT_ID, VERSION_NEW);

    private static final Map<String, ProjectVersionRef> DEPENDENCIES = Collections.singletonMap(
            GAV,
            SimpleProjectVersionRef.parse(GAV_NEW));

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo(TEST_URL + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(HttpStatus.OK)
                                        .withHeader(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                                        .withBody(readSampleDAResponse(RESPONSE_FILE_NAME))));
        stubFor(
                post(urlEqualTo(TEST_URL + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(HttpStatus.OK)
                                        .withHeader(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                                        .withBody(readSampleDAResponse(RESPONSE_PROJECT_FILE_NAME))));

        System.setProperty(Configuration.DA, DA_HOST + ":" + wireMockRule.port() + TEST_URL);
    }

    @Test
    public void verifyWar()
            throws IOException, ManipulationException, URISyntaxException, GitAPIException {
        final Path projectRoot = tempDir.newFolder(PROJECT_NAME).toPath();
        final Map<String, String> properties = new HashMap<>();

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot.toFile(),
                projectRoot.getFileName().toString(),
                properties);

        assertThat(projectRoot.resolve(AlignmentTask.GME)).exists();
        assertThat(projectRoot.resolve(AlignmentTask.GRADLE).resolve(AlignmentTask.GME_REPOS)).exists();
        assertThat(projectRoot.resolve(AlignmentTask.GME_PLUGINCONFIGS)).exists();
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot.toFile()));
        assertThat(FileUtils.getLastLine(projectRoot.resolve(Project.DEFAULT_BUILD_FILE).toFile()))
                .isEqualTo(AlignmentTask.INJECT_GME_END);
        assertThat(systemOutRule.getLinesNormalized())
                .contains("Passing 1 GAVs into the REST client api [" + GAV + "]");
        assertThat(alignmentModel).isNotNull();
        assertThat(alignmentModel.getAlignedDependencies()).containsExactlyEntriesOf(DEPENDENCIES);
    }
}
