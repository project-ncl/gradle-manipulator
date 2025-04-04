package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.gradle.util.GradleVersion;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;

@RunWith(BMUnitRunner.class)
public class MicrometerProjectFunctionalTest extends AbstractWiremockTest {
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
                        .withBody(readSampleDAResponse("micrometer-project-da-response.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("micrometer-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated()
            throws IOException, URISyntaxException, GitAPIException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.9")) >= 0);

        final File projectRoot = tempDir.newFolder();

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/micrometer-metrics/micrometer.git")
                .setDirectory(projectRoot)
                .setBranch("v1.14.5")
                .setBranchesToClone(Collections.singletonList("refs/tags/v1.14.5"))
                .setDepth(1)
                .setNoTags()
                .call()) {
            System.out.println("Cloned Micrometer to " + projectRoot);
        }

        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource("micrometer-project").toURI()).toFile(), projectRoot);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("overrideTransitive", "true");
        parameters.put("-Prelease.stage", "final");
        parameters.put("-Prelease.version", "1.14.5");
        parameters.put("-Prelease.useLastTag", "false");
        parameters.put("-Prelease.disableGitChecks", "true");
        TestManipulationModel alignmentModel = TestUtils.align(projectRoot, false, parameters);

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + File.separator + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("1.14.5");
            assertThat(am.getVersion()).isEqualTo("1.14.5.redhat-00002");
            assertThat(am.getGroup()).isEqualTo("io.micrometer");
            assertThat(am.getName()).isEqualTo("micrometer");
            assertThat(am.findCorrespondingChild(":micrometer-core")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.14.5.redhat-00002");
                assertThat(root.getName()).isEqualTo("micrometer-core");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(tuple("netty-bom", "4.1.119.Final-redhat-00001"));
            });
        });
    }
}
