package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

/**
 * Aligns a project with Ivy dependency (without groupId).
 *
 * Currently it is only expected that the build doesn't fail, and dependency is not aligned.
 */
public class IvyDependencyFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-da-response.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + AbstractWiremockTest.PORT + "/da/rest/v-1");
    }

    @Test
    public void test() throws IOException, URISyntaxException {
        final File projectRoot = tempDir.newFolder("ivy-dependency");

        final File settings = new File(projectRoot, "settings.xml");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(),
                Collections.emptyMap());

        assertThat(alignmentModel.findCorrespondingChild("root")).satisfies(root -> {
            assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
            assertThat(root.getName()).isEqualTo("root");
            assertThat(settings).exists();
            final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
            assertThat(alignedDependencies)
                    .isEmpty();
        });

        assertTrue(FileUtils.readFileToString(settings, Charset.defaultCharset())
                .contains("http://download.eclipse.org/eclipse/updates/4.6/R-4.6.3-201703010400/plugins/"));

    }

    @Test
    public void testNoRepoBackup() throws IOException, URISyntaxException {
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
