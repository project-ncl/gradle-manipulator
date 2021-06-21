package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
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

public class ComplexProjectFunctionalTest extends AbstractWiremockTest {

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
                        .withBody(readSampleDAResponse("complex-project-da-response.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("complex-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated()
            throws IOException, URISyntaxException, XmlPullParserException, ManipulationException {
        final File projectRoot = tempDir.newFolder("complex-project");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());

        assertEquals(AlignmentTask.INJECT_GME_START, TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END, FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.jboss.gm.analyzer.functest");
            assertThat(am.getName()).isEqualTo("complex");
            assertThat(am.findCorrespondingChild("complex")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.0.0.redhat-00004");
                assertThat(root.getName()).isEqualTo("complex");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .hasSize(5)
                        .extracting("artifactId", "versionString")
                        .contains(
                                // ensure that the aligned versions as are always used for dynamic and regular dependencies
                                tuple("undertow-core", "2.0.19.Final-redhat-00001"),
                                tuple("spring-boot-dependencies", "2.1.4.RELEASE.redhat-3"),
                                tuple("hibernate-core", "5.3.9.Final-redhat-00001"))
                        // we can't assert on a specific version because we have used a range as the dependency's version
                        .filteredOn(t -> !getVersion(t).contains("redhat"))
                        .satisfies(l -> {
                            // make sure the two dynamic dependencies not aligned exist and have versions similar to what we expect

                            assertThat(l).filteredOn(t -> "HdrHistogram".equals(getArtifactId(t)))
                                    .hasOnlyOneElementSatisfying(t -> assertThat(getVersion(t)).startsWith("2."));
                            assertThat(l).filteredOn(t -> "commons-lang3".equals(getArtifactId(t)))
                                    .hasOnlyOneElementSatisfying(t -> assertThat(getVersion(t)).startsWith("3."));
                        });

                assertThat(root.getAlignedDependencies().keySet()).containsOnly(
                        "org.apache.commons:commons-lang3:latest.release",
                        "org.hdrhistogram:HdrHistogram:2.+",
                        "org.springframework.boot:spring-boot-dependencies:2.1.4.RELEASE",
                        "io.undertow:undertow-core:[2.0.0, 2.0.20)",
                        "org.hibernate:hibernate-core:5.3.7.Final");
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
                "https://dl.google.com/dl/android/maven2/",
                "https://jcenter.bintray.com/");
    }

    private String getArtifactId(Tuple tuple) {
        return tuple.toList().get(0).toString();
    }

    private String getVersion(Tuple tuple) {
        return tuple.toList().get(1).toString();
    }
}
