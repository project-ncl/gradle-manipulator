package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.gradle.util.GradleVersion;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class GMEFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubDACall();

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    private void stubDACall() throws IOException, URISyntaxException {
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("spring-like-layout-da-root.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("spring-like-layout-da-root-project.json"))));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun() throws IOException {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);

        final File projectRoot = tempDir.newFolder("gme-like-project");

        // This rather messy block copies just the relevant gradle files and the git repository from GME itself
        // Finally it modifies the root gradle file to inject the plugin.
        org.apache.commons.io.FileUtils.copyDirectory(
                new File(TestUtils.class.getClassLoader().getResource("simple-project-da-response.json").getPath())
                        .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile(),
                projectRoot,
                FileFilterUtils.or(DirectoryFileFilter.DIRECTORY, FileFilterUtils.nameFileFilter("gradle.properties"),
                        FileFilterUtils.suffixFileFilter("kts")));
        org.apache.commons.io.FileUtils.copyDirectory(
                new File(new File(TestUtils.class.getClassLoader().getResource("simple-project-da-response.json").getPath())
                        .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile(), ".git"),
                new File(projectRoot, ".git"));
        File rootBuildFile = new File(projectRoot, "build.gradle.kts");
        try (Stream<String> lines = Files.lines(rootBuildFile.toPath())) {
            final List<String> linesList = lines.map(l -> l.replaceAll("(id[(]\"com.adarshr.test-logger)",
                    "id(\"org.jboss.gm.analyzer\") \n $1")).map(l -> l.replaceAll("(apply[(]plugin = \"idea\")",
                            "apply(plugin = \"org.jboss.gm.analyzer\") \n $1"))
                    .collect(Collectors.toList());
            Files.write(rootBuildFile.toPath(), linesList);
        }

        final Map<String, String> map = new HashMap<>();
        map.put("overrideTransitive", "false");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, false, map);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.jboss.gm");
            assertThat(am.getName()).isEqualTo("gradle-manipulator");
            assertThat(am.getChildren().keySet()).hasSize(4).containsExactlyInAnyOrder("common", "analyzer", "manipulation",
                    "cli");
        });

        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));
    }
}
