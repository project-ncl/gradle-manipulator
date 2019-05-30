package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
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

public class ComplexProjectFunctionalTest extends AbstractWiremockTest {

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
                        .withBody(readSampleDAResponse("complex-project-da-response.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + AbstractWiremockTest.PORT + "/da/rest/v-1");
    }

    @Test
    // Note : if this test has started failing check the latest version of undertow on
    // http://central.maven.org/maven2/io/undertow/undertow-core/
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException, XmlPullParserException {
        final File projectRoot = tempDir.newFolder("complex-project");
        final ManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.LOAD_GME, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.jboss.gm.analyzer.functest");
            assertThat(am.getName()).isEqualTo("complex");
            assertThat(am.findCorrespondingChild("complex")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.0.redhat-00004");
                assertThat(root.getName()).isEqualTo("complex");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("undertow-core", "2.0.21.Final-redhat-00001"),
                                tuple("spring-boot-dependencies", "2.1.4.RELEASE.redhat-3"),
                                tuple("hibernate-core", "5.3.9.Final-redhat-00001"));
            });
        });

        // check that generated settings.xml contains correct remote repositories
        File settingsFile = new File(projectRoot, "settings.xml");
        SettingsXpp3Reader reader = new SettingsXpp3Reader();
        Settings generatedSettings = reader.read(new FileInputStream(settingsFile));
        List<Repository> repositories = generatedSettings.getProfiles().get(0).getRepositories();
        assertThat(repositories).extracting("url").containsOnly(
                "https://repo.maven.apache.org/maven2/",
                "https://oss.sonatype.org/content/repositories/snapshots/",
                "https://localhost:8089/ivy-repo",
                "https://plugins.gradle.org/m2/");
    }
}
