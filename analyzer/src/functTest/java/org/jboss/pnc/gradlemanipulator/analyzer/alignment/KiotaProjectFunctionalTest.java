package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

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
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class KiotaProjectFunctionalTest extends AbstractWiremockTest {

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
                                        .withBody(readSampleDAResponse("kiota-project-da-response.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("kiota-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated()
            throws IOException, URISyntaxException, GitAPIException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.3")) >= 0);

        final File projectRoot = tempDir.newFolder();

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/microsoft/kiota-java.git")
                .setDirectory(projectRoot)
                .setBranch("v1.9.0")
                .setBranchesToClone(Collections.singletonList("refs/tags/v1.9.0"))
                .setDepth(1)
                .setNoTags()
                .call()) {
            System.out.println("Cloned Kiota to " + projectRoot);
        }

        FileUtils.copyDirectory(
                Paths
                        .get(TestUtils.class.getClassLoader().getResource("kiota-java-project").toURI())
                        .toFile(),
                projectRoot);

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                false);

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + File.separator + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();

        assertThat(systemOutRule.getLinesNormalized()).contains(
                "project ':components:serialization:json' is using an unspecified version; default to root project version of 1.9.0-SNAPSHOT");
        assertThat(systemOutRule.getLinesNormalized()).contains(
                "Project 'com.microsoft.kiota:kiota-java:1.9.0-SNAPSHOT' is defined but no publication ; skipping");

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("1.9.0-SNAPSHOT");
            assertThat(am.getVersion()).isEqualTo("1.9.0.redhat-00002");
            assertThat(am.getGroup()).isEqualTo("com.microsoft.kiota");
            assertThat(am.getName()).isEqualTo("kiota-java");
            assertThat(am.findCorrespondingChild(":components:serialization:json")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.9.0.redhat-00002");
                assertThat(root.getName()).isEqualTo("microsoft-kiota-serialization-json");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("jakarta.annotation-api", "2.1.1.redhat-00005"),
                                tuple("slf4j-simple", "2.0.17.redhat-00001"));
            });
        });
    }
}
