package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.Project;
import org.gradle.testkit.runner.GradleRunner;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.io.ManipulationIO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot, projectRoot.getName(),
                Collections.singletonMap("dependencyOverride.com.yammer.metrics:*@org.acme.gradle:root", ""));

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GRADLE + '/' + AlignmentTask.GME_REPOS).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END,
                org.jboss.gm.common.utils.FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("1.0.1");
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

        TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        List<String> lines = FileUtils.readLines(new File(projectRoot, Project.DEFAULT_BUILD_FILE), Charset.defaultCharset());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START, org.jboss.gm.common.utils.FileUtils.getFirstLine(lines));
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root"))
                    .satisfies(root -> assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002"));
        });

        // ensure we don't try to apply the manipulation plugin which in all likelihood isn't even available on the classpath
        System.setProperty("org.gradle.project.gmeAnalyse", "true");
        GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments("--stacktrace", "--info", AlignmentTask.NAME)
                //.withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();
        alignmentModel = new TestManipulationModel(ManipulationIO.readManipulationModel(projectRoot));
        lines = FileUtils.readLines(new File(projectRoot, Project.DEFAULT_BUILD_FILE), Charset.defaultCharset());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root"))
                    .satisfies(root -> assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00003"));
        });
        assertEquals(AlignmentTask.INJECT_GME_START, org.jboss.gm.common.utils.FileUtils.getFirstLine(lines));

        assertThat(lines).filteredOn(l -> l.startsWith(AlignmentTask.INJECT_GME_START)).hasSize(1);
    }

    @Test
    public void verifySnapshotHandling() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project");
        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);
        File props = new File(projectRoot, "gradle.properties");
        FileUtils.writeStringToFile(props,
                FileUtils.readFileToString(props, Charset.defaultCharset()).replace(
                        "version=1.0.1", "version=1.0.1-SNAPSHOT"),
                Charset.defaultCharset());

        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, false);

        verify(postRequestedFor(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .withRequestBody(notMatching(".*SNAPSHOT.*")));

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GRADLE + '/' + AlignmentTask.GME_REPOS).exists());
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
            });
        });
    }
}
