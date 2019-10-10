package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.Project;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.utils.FileUtils;
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

public class DynamicWithLocksProjectFunctionalTest extends AbstractWiremockTest {

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
                        .withBody(readSampleDAResponse("dynamic-project.with-locks.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + AbstractWiremockTest.PORT + "/da/rest/v-1");
    }

    @Test
    // Note : if this test has started failing check the latest version of undertow on
    // http://central.maven.org/maven2/io/undertow/undertow-core/
    public void ensureAlignmentFileCreated()
            throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("dynamic-project-with-locks");

        //noinspection ConstantConditions
        org.apache.commons.io.FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);

        final File gitDir = new File(projectRoot, "dotgit");
        Files.move(gitDir.toPath(), projectRoot.toPath().resolve(".git"));
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), false);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());

        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.jboss.gm.analyzer.functest");
            assertThat(am.getName()).isEqualTo("undertow");
            assertThat(am.findCorrespondingChild("undertow")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.0.redhat-00001");
                assertThat(root.getName()).isEqualTo("undertow");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                // ensure that the aligned versions as are always used for dynamic and regular dependencies
                                tuple("undertow-core", "2.0.21.Final-redhat-00002"),
                                tuple("commons-lang3", "3.8-redhat-00001"),
                                tuple("resteasy-jaxrs", "3.6.3.SP1-redhat-00010"),
                                tuple("HdrHistogram", "2.1.10"));

                assertThat(root.getAlignedDependencies().keySet()).containsOnly(
                        "org.apache.commons:commons-lang3:latest.release",
                        "org.hdrhistogram:HdrHistogram:2.+",
                        "io.undertow:undertow-core:2.0+",
                        "org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1");
            });
        });

        // make sure the project name was added
        assertEquals("rootProject.name='undertow'",
                FileUtils.getLastLine(new File(projectRoot, "settings.gradle")));
    }
}
