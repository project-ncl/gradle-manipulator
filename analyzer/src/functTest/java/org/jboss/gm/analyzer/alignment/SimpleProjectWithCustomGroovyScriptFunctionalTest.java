package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.gradle.api.Project;
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
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class SimpleProjectWithCustomGroovyScriptFunctionalTest extends AbstractWiremockTest {

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
                        .withBody(readSampleDAResponse("simple-project-with-custom-groovy-script-da-response.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-with-custom-groovy-script-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void verifyGroovyInjection() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project-with-custom-groovy-script");

        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(),
                Collections.singletonMap("groovyScripts", "file://" + projectRoot.getAbsolutePath() + "/gme.groovy"));

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END,
                org.jboss.gm.common.utils.FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("newRoot");
            assertThat(am.findCorrespondingChild("newRoot")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
                assertThat(root.getName()).isEqualTo("newRoot");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("undertow-core", "2.0.15.Final-redhat-00001"),
                                tuple("hibernate-core", "5.3.7.Final-redhat-00001"));
            });
        });

        // verify that the custom groovy script altered the build script
        final List<String> lines = FileUtils.readLines(new File(projectRoot, "build.gradle"), Charset.defaultCharset());
        assertThat(lines).filteredOn(
                l -> l.contains("new CustomVersion"))
                .hasOnlyOneElementSatisfying(e -> assertThat(e).contains("CustomVersion( '1.0.1.redhat-00002', project )"));
        assertThat(lines).filteredOn(l -> l.contains("undertowVersion ="))
                .hasOnlyOneElementSatisfying(l -> assertThat(l).contains("2.0.15.Final-redhat-00001"));
        assertTrue(lines.stream().anyMatch(s -> s.contains("CustomVersion( '1.0.1.redhat-00002', project )")));
        assertTrue(systemOutRule.getLog().contains("Attempting to read URL"));
        assertTrue(systemOutRule.getLog().contains("found new version is 1.0.1.redhat-00002"));
        assertTrue(systemOutRule.getLog().contains("original version is 1.0.1" + System.lineSeparator()));
        assertThat(FileUtils.readFileToString(new File(projectRoot, "build.gradle"), Charset.defaultCharset()))
                .contains("classpath \"org.hibernate:hibernate-core:5.3.7.Final-redhat-00001")
                .contains("publishing {" + System.lineSeparator() +
                        "  publications {" + System.lineSeparator() +
                        "    nebula {" + System.lineSeparator() +
                        "      artifact(\"build/distributions/${project.name}-${version}.zip\")" + System.lineSeparator());
        assertThat(FileUtils.readFileToString(new File(projectRoot, "settings.gradle"), Charset.defaultCharset()))
                .doesNotContain("x-pack").contains("another-pack");
        assertTrue(systemOutRule.getLog().contains("Retrieved"));
    }
}
