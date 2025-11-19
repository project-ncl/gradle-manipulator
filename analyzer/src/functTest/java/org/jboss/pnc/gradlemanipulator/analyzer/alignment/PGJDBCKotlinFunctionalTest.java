package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import org.gradle.util.GradleVersion;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.utils.FileUtils;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class PGJDBCKotlinFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubDACall();

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    private void stubDACall() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("spring-like-layout-da-root.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("spring-like-layout-da-root-project.json"))));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun()
            throws IOException, URISyntaxException, ManipulationException {
        // XXX:
        // Script compilation errors:
        //
        //   Line 17:     when (extra.has(name)) {
        //                      ^ Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:
        //                          public val ExtensionAware.extra: ExtraPropertiesExtension defined in org.gradle.kotlin.dsl
        //
        //   Line 18:         true -> extra.get(name) as? String
        //                            ^ Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:
        //                                public val ExtensionAware.extra: ExtraPropertiesExtension defined in org.gradle.kotlin.dsl
        //
        // 2 errors
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0);
        // XXX: Caused by: java.lang.ClassNotFoundException: org.gradle.api.tasks.SourceSet
        // XXX: See <https://github.com/gradle/gradle/issues/6862>
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("5.3")) >= 0);

        final File projectRoot = tempDir.newFolder("pgjdbc");
        final File buildFile = new File(projectRoot, "build.gradle.kts");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GRADLE + '/' + AlignmentTask.GME_REPOS).exists());
        assertTrue(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS).exists());
        assertEquals(AlignmentTask.INJECT_GME_END_KOTLIN, FileUtils.getLastLine(buildFile));
        List<String> lines = org.apache.commons.io.FileUtils.readLines(buildFile, Charset.defaultCharset());
        assertEquals(1, lines.stream().filter(s -> s.contains("buildscript")).count());

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.postgresql");
            assertThat(am.getName()).isEqualTo("pgjdbc");
            assertThat(am.getVersion()).isEqualTo("42.2.14.redhat-00001");
        });
    }
}
