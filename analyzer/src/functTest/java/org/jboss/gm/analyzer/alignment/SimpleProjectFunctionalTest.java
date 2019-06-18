package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.Project;
import org.gradle.testkit.runner.GradleRunner;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(BMUnitRunner.class)
public class SimpleProjectFunctionalTest extends AbstractWiremockTest {

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
                        .withBody(readSampleDAResponse("simple-project-da-response.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + AbstractWiremockTest.PORT + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project");

        final ManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GME_REPOS).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END,
                org.jboss.gm.common.utils.FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
                assertThat(root.getName()).isEqualTo("root");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("undertow-core", "2.0.15.Final-redhat-00001"),
                                tuple("hibernate-core", "5.3.7.Final-redhat-00001"));
            });
        });

        //verify that dummy.gradle now includes the gme-repos.gradle injection
        final File extraGradleFile = projectRoot.toPath().resolve("gradle/dummy.gradle").toFile();
        assertThat(extraGradleFile).exists();
        assertThat(FileUtils.readLines(extraGradleFile, Charset.defaultCharset()))
                .filteredOn(l -> l.trim().equals(AlignmentTask.APPLY_GME_REPOS)).hasSize(1);

    }

    @Test
    // @BMUnitConfig(verbose = true, bmunitVerbose = true)
    @BMRule(name = "override-inprocess-configuration",
            targetClass = "org.jboss.gm.common.Configuration",
            isInterface = true,
            targetMethod = "ignoreUnresolvableDependencies()",
            targetLocation = "AT ENTRY",
            action = "RETURN true")
    public void verifySingleGMEInjection() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project");

        ManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        List<String> lines = FileUtils.readLines(new File(projectRoot, Project.DEFAULT_BUILD_FILE), Charset.defaultCharset());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, org.jboss.gm.common.utils.FileUtils.getFirstLine(lines));
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root"))
                    .satisfies(root -> assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002"));
        });

        GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments("--stacktrace", "--info", AlignmentTask.NAME)
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();
        alignmentModel = ManipulationIO.readManipulationModel(projectRoot);
        lines = FileUtils.readLines(new File(projectRoot, Project.DEFAULT_BUILD_FILE), Charset.defaultCharset());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root"))
                    .satisfies(root -> assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00003"));
        });
        assertEquals(AlignmentTask.INJECT_GME_START, org.jboss.gm.common.utils.FileUtils.getFirstLine(lines));

        int counter = 0;
        for (String l : lines) {
            if (l.trim().equals(AlignmentTask.INJECT_GME_START)) {
                counter++;
            }
        }
        assertEquals(1, counter);
    }

}
