package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.jboss.gm.analyzer.alignment.TestUtils.copyDirectory;

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

public class MultiModuleProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() {
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .inScenario("multi-module")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("multi-module-da-root.json")))
                .willSetStateTo("project root called"));

        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .inScenario("multi-module")
                .whenScenarioStateIs("project root called")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("multi-module-da-subproject1.json")))
                .willSetStateTo("first dependency called"));

        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .inScenario("multi-module")
                .whenScenarioStateIs("first dependency called")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("multi-module-da-subproject2.json"))));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun() throws IOException, URISyntaxException {
        final File simpleProjectRoot = tempDir.newFolder("multi-module");
        copyDirectory("multi-module", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        final BuildResult buildResult = GradleRunner.create()
                .withProjectDir(simpleProjectRoot)
                .withArguments(AlignmentTask.NAME)
                .withDebug(true)
                .withPluginClasspath()
                .build();

        assertThat(buildResult.task(":" + AlignmentTask.NAME).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(buildResult.getOutput()).containsIgnoringCase("Starting alignment task");

        final AlignmentModel alignmentModel = AlignmentUtils.getAlignmentModelAt(simpleProjectRoot.toPath().toFile());
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");

            assertThat(am.getModules()).hasSize(3).extracting("name").containsExactly("root", "subproject1", "subproject2");

            assertThat(am.getModules()).satisfies(ml -> {
                assertThat(ml.get(0)).satisfies(root -> {
                    assertThat(root.getNewVersion()).isEqualTo("1.1.2-redhat-00004");
                    assertThat(root.getAlignedDependencies()).isEmpty();
                });

                assertThat(ml.get(1)).satisfies(subproject1 -> {
                    assertThat(subproject1.getNewVersion()).isEqualTo("1.1.2-redhat-00004");
                    final Collection<ProjectVersionRef> alignedDependencies = subproject1.getAlignedDependencies().values();
                    assertThat(alignedDependencies)
                            .extracting("artifactId", "versionString")
                            .containsOnly(
                                    tuple("spring-context", "5.1.6.RELEASE-redhat-00005"),
                                    tuple("hibernate-core", "5.4.2.Final-redhat-00001"));
                });

                assertThat(ml.get(2)).satisfies(subproject2 -> {
                    assertThat(subproject2.getNewVersion()).isEqualTo("1.1.2-redhat-00004");
                    final Collection<ProjectVersionRef> alignedDependencies = subproject2.getAlignedDependencies().values();
                    assertThat(alignedDependencies)
                            .extracting("artifactId", "versionString")
                            .containsOnly(
                                    tuple("resteasy-jaxrs", "3.6.3.SP1-redhat-00001"));
                });
            });
        });
    }

}
