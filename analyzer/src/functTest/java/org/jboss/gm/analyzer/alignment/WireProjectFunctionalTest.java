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
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.JVMTestSetup;
import org.jboss.gm.common.io.ManipulationIO;
import org.junit.Before;
import org.junit.BeforeClass;
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
import static org.jboss.gm.common.JVMTestSetup.JDK17_DIR;
import static org.junit.Assume.assumeTrue;

public class WireProjectFunctionalTest extends AbstractWiremockTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @BeforeClass
    public static void setupJVM() throws IOException {
        JVMTestSetup.setupJVM();
    }

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("wire-project-da-response.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("wire-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated()
            throws IOException, URISyntaxException, GitAPIException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.9")) >= 0);
        // Swift compilation issues after 8.12
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.13")) < 0);

        final File projectRoot = tempDir.newFolder();

        try (Git ignored = Git.cloneRepository()
                .setURI("https://github.com/square/wire.git")
                .setDirectory(projectRoot)
                .setBranch("5.1.0")
                .setBranchesToClone(Collections.singletonList("refs/tags/5.1.0"))
                .setDepth(1)
                .setNoTags()
                .call()) {
            System.out.println("Cloned Wire to " + projectRoot);
        }

        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource("wire-project").toURI()).toFile(), projectRoot);

        // Can't use TestUtils as we need to fork so need to set debug to false.
        Map<String, String> parameters = new HashMap<>();
        parameters.put("overrideTransitive", "true");
        parameters.put("org.gradle.java.home", JDK17_DIR.toString());
        parameters.put("ignoreUnresolvableDependencies", "true");
        parameters.put("kjs", "false");
        parameters.put("knative", "false");
        parameters.put("-Pswift", "false");
        parameters.put("--quiet", "");

        final GradleRunner runner = TestUtils
                .createGradleRunner(projectRoot, parameters)
                .withDebug(false);
        final BuildResult buildResult = runner.build();
        final BuildTask task = buildResult.task(":" + AlignmentTask.NAME);

        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final TestManipulationModel alignmentModel = new TestManipulationModel(
                ManipulationIO.readManipulationModel(projectRoot));

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + File.separator + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("5.1.0");
            assertThat(am.getVersion()).isEqualTo("5.1.0.redhat-00002");
            assertThat(am.getGroup()).isEqualTo("com.squareup.wire");
            assertThat(am.getName()).isEqualTo("wire");
            assertThat(am.findCorrespondingChild(":wire-golden-files")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("5.1.0.redhat-00002");
                assertThat(root.getName()).isEqualTo("wire-golden-files");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(tuple("error_prone_annotations", "2.21.1.redhat-00001"));
            });
        });
    }
}
