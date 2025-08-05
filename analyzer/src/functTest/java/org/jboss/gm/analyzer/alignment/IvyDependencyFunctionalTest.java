package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

/**
 * Aligns a project with Ivy dependency (without groupId).
 *
 * Currently, it is only expected that the build doesn't fail and the dependency is not aligned.
 */
public class IvyDependencyFunctionalTest extends AbstractWiremockTest {

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
    public void test() throws IOException, URISyntaxException {
        // XXX: Use of patternLayout()
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File projectRoot = tempDir.newFolder("ivy-dependency");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                projectRoot.getName(),
                Collections.emptyMap());

        assertThat(alignmentModel.findCorrespondingChild("root")).satisfies(root -> {
            assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
            assertThat(root.getName()).isEqualTo("root");
            final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
            assertThat(alignedDependencies).isEmpty();
        });

        final Path settings = projectRoot.toPath().resolve("settings.xml");
        assertThat(settings).isRegularFile().isReadable();
        assertThat(Files.readAllBytes(settings)).asString(StandardCharsets.UTF_8)
                .contains("https://download.eclipse.org/eclipse/updates/4.6/R-4.6.3-201703010400/plugins/");
    }

    @Test
    public void testNoRepoBackup() throws IOException, URISyntaxException {
        // XXX: Use of patternLayout()
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File projectRoot = tempDir.newFolder("ivy-dependency");

        final File settings = new File(projectRoot, "settings.xml");
        final Map<String, String> props = Collections.singletonMap("repoRemovalBackup", "");

        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), props);

        assertThat(alignmentModel.findCorrespondingChild("root")).satisfies(root -> {
            assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
            assertThat(root.getName()).isEqualTo("root");
            assertThat(settings).doesNotExist();
            final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
            assertThat(alignedDependencies)
                    .isEmpty();
        });
    }
}
