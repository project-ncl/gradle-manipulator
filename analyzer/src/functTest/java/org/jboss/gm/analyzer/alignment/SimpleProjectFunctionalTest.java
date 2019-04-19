package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.alignment.AlignmentModel;
import org.jboss.gm.common.alignment.AlignmentUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SimpleProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() {
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-da-response.json"))));
    }

    @Test
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException {
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        TestUtils.copyDirectory("simple-project", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = GradleRunner.create()
                .withProjectDir(simpleProjectRoot)
                .withArguments(AlignmentTask.NAME)
                .withDebug(true)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + AlignmentTask.NAME).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final AlignmentModel alignmentModel = AlignmentUtils.getAlignmentModelAt(simpleProjectRoot.toPath().toFile());
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.getModules()).hasSize(1).satisfies(ml -> {
                assertThat(ml.get(0)).satisfies(root -> {
                    assertThat(root.getNewVersion()).isEqualTo("1.0.1-redhat-00001");
                    assertThat(root.getName()).isEqualTo("root");
                    final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                    assertThat(alignedDependencies)
                            .extracting("artifactId", "versionString")
                            .containsOnly(
                                    tuple("undertow-core", "2.0.15.Final-redhat-00001"),
                                    tuple("hibernate-core", "5.3.7.Final-redhat-00001"));
                });
            });
        });
    }
}
