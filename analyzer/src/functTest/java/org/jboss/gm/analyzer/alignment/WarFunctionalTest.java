package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import kong.unirest.ContentType;
import kong.unirest.HeaderNames;
import kong.unirest.HttpStatus;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.gradle.api.Project;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.utils.FileUtils;
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

        // On a new release the SNAPSHOT artifact (e.g. 3.2-SNAPSHOT) for the manipulation library
        // may not be available causing this to fail. We can't do the same as DifferentJVMTest as
        // we need the manipulation library built. Hence, we'll use JGit to establish the last tag
        // (i.e. release) and use that.
        Collection<Ref> refs2 = Git.lsRemoteRepository()
                .setRemote("https://github.com/project-ncl/gradle-manipulator.git")
                .setTags(true)
                .call();
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        String name = refs2.stream().map(Ref::getName).max(Comparator.naturalOrder()).get();
        properties.put("manipulationVersion", name.substring(name.lastIndexOf('/') + 1));

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

        verifyWar(projectRoot);
    }

    private static void verifyWar(Path projectRoot) throws IOException {
        final BuildResult buildResult = TestUtils.createGradleRunner(projectRoot.toFile(), Collections.emptyMap())
                .withArguments("war")
                .build();

        assertThat(Objects.requireNonNull(buildResult.task(":war")).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final Path warPath = Paths.get("build", "libs", PROJECT_NAME + ".war");
        final Path warFile = projectRoot.resolve(warPath);

        assertThat(warFile).isRegularFile().isReadable();

        try (final ZipFile zipFile = new ZipFile(warFile.toFile())) {
            assertThat(
                    zipFile.stream()
                            .filter(zipEntry -> !zipEntry.isDirectory() && zipEntry.getName().endsWith(".jar"))
                            .map(ZipEntry::getName))
                    .containsExactly("WEB-INF/lib/" + ARTIFACT_ID + "-" + VERSION_NEW + ".jar");
        }
    }
}
