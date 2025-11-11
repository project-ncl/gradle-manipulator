package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class CELProjectFunctionalTest extends AbstractWiremockTest {

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
                                        .withBody(readSampleDAResponse("cel-project-da-response.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("cel-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated()
            throws IOException, URISyntaxException, GitAPIException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.5")) >= 0);
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("9.0.0")) < 0);

        final File projectRoot = tempDir.newFolder();

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/projectnessie/cel-java.git")
                .setDirectory(projectRoot)
                .setBranch("v0.4.5")
                .setBranchesToClone(Collections.singletonList("refs/tags/v0.4.5"))
                .setDepth(1)
                .setNoTags()
                .call()) {
            System.out.println("Cloned CEL-Java to " + projectRoot);
        }

        FileUtils.copyDirectory(
                Paths
                        .get(TestUtils.class.getClassLoader().getResource("cel-java-project").toURI())
                        .toFile(),
                projectRoot);

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                false,
                Collections.singletonMap("overrideTransitive", "true"));

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + File.separator + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("0.4.5");
            assertThat(am.getVersion()).isEqualTo("0.4.5.redhat-00002");
            assertThat(am.getGroup()).isEqualTo("org.projectnessie.cel");
            assertThat(am.getName()).isEqualTo("cel-parent");
            assertThat(am.findCorrespondingChild(":cel-tools")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("0.4.5.redhat-00002");
                assertThat(root.getName()).isEqualTo("cel-tools");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("agrona", "1.22.0.redhat-00002"),
                                tuple("byte-buddy", "1.14.18.redhat-00002"),
                                tuple("asm-commons", "9.7.0.redhat-00002"),
                                tuple("asm-tree", "9.7.0.redhat-00002"),
                                tuple("asm", "9.7.0.redhat-00002"));
            });
        });
    }
}
