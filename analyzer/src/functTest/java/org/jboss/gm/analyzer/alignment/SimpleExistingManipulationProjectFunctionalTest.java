package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
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
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(BMUnitRunner.class)
//@BMUnitConfig(verbose = true, bmunitVerbose = true)
public class SimpleExistingManipulationProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();//.muteForSuccessfulTests();

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
    @BMRule(name = "override-inprocess-configuration",
            targetClass = "org.jboss.gm.common.Configuration",
            isInterface = true,
            targetMethod = "dependencyConfiguration()",
            targetLocation = "AT ENTRY",
            action = "return DependencyState$DependencyPrecedence.NONE")
    public void versionUpdate() throws IOException, URISyntaxException {
        final File projectRoot = tempDir.newFolder("simple-existing-manipulation");

        final ManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), false);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.LOAD_GME, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002");
                assertThat(root.getName()).isEqualTo("root");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies.size() == 0);
            });
        });

        // we care about how many calls are made to DA from an implementation perspective which is why we assert
        verify(0, postRequestedFor(urlEqualTo("/da/rest/v-1/reports/lookup/gavs")));
    }

    @Test
    @BMRules(rules = {
            @BMRule(name = "override-dependency-configuration",
                    targetClass = "org.jboss.gm.common.Configuration",
                    isInterface = true,
                    targetMethod = "dependencyConfiguration()",
                    targetLocation = "AT ENTRY",
                    action = "return DependencyState$DependencyPrecedence.NONE"),
            @BMRule(name = "override-version-configuration",
                    targetClass = "org.jboss.gm.common.Configuration",
                    isInterface = true,
                    targetMethod = "versionModificationEnabled()",
                    targetLocation = "AT ENTRY",
                    action = "return false")
    })
    public void noVersionUpdate() throws IOException, URISyntaxException {
        final File projectRoot = tempDir.newFolder("simple-existing-manipulation");

        final ManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), false);

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.LOAD_GME, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo(null);
                assertThat(root.getName()).isEqualTo("root");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies.size() == 0);
            });
        });

        // we care about how many calls are made to DA from an implementation perspective which is why we assert
        verify(0, postRequestedFor(urlEqualTo("/da/rest/v-1/reports/lookup/gavs")));
    }
}
