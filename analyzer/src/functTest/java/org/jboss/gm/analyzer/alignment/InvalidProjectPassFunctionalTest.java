package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.common.alignment.Utils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

// See ensureInvalidNoException method for explanation of this rule.
@RunWith(BMUnitRunner.class)
@BMUnitConfig(verbose = true, bmunitVerbose = true)
@BMRule(name = "override-inprocess-configuration",
        targetClass = "org.jboss.gm.common.Configuration",
        isInterface = true,
        targetMethod = "ignoreUnresolvableDependencies()",
        targetLocation = "AT ENTRY",
        action = "RETURN true")
public class InvalidProjectPassFunctionalTest extends AbstractWiremockTest {

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
                        .withBody(readSampleDAResponse("invalid-project-da-response.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + AbstractWiremockTest.PORT + "/da/rest/v-1");
    }

    @Test
    public void ensureInvalidNoException() throws IOException, URISyntaxException {

        // In theory, setting this property should have been sufficient but we are currently running the tests
        // in-process to allow for debugging. This prevents the configuration from being applied when
        // running multiple (running single in IntelliJ works fine). To work around we use a Byteman script.
        // System.setProperty("ignoreUnresolvableDependencies", "true");

        final File projectRoot = tempDir.newFolder("invalid-project");
        final ManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.LOAD_GME, Utils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.jboss.gm.analyzer.functest");
            assertThat(am.getName()).isEqualTo("invalid");
            assertThat(am.findCorrespondingChild("invalid")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00001");
                assertThat(root.getName()).isEqualTo("invalid");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertEquals(0, alignedDependencies.size());
            });
        });
    }

}
