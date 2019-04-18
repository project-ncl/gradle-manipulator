package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.analyzer.alignment.TestUtils.copyDirectory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.alignment.AlignmentModel;
import org.jboss.gm.common.alignment.SerializationUtils;
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

        final Path alignmentFilePath = simpleProjectRoot.toPath().resolve("alignment.json");
        assertThat(alignmentFilePath).isRegularFile();

        final AlignmentModel alignmentModel = SerializationUtils.getObjectMapper()
                .readValue(alignmentFilePath.toFile(), AlignmentModel.class);
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getBasicInfo()).isNotNull().satisfies(b -> {
                assertThat(b.getGroup()).isEqualTo("org.acme");
                assertThat(b.getName()).isEqualTo("root");
            });
            assertThat(am.getModules()).hasSize(3).extracting("name").containsExactly("root", "subproject1", "subproject2");

            assertThat(am.getModules()).satisfies(ml -> {
                assertThat(ml.get(0)).satisfies(root -> {
                    assertThat(root.getNewVersion()).isEqualTo("1.1.2-redhat-00004");
                });

                assertThat(ml.get(1)).satisfies(subproject1 -> {
                    assertThat(subproject1.getNewVersion()).isEqualTo("1.1.2-redhat-00004");
                });

                assertThat(ml.get(2)).satisfies(subproject1 -> {
                    assertThat(subproject1.getNewVersion()).isEqualTo("1.1.2-redhat-00004");
                });
            });
        });
    }

}
