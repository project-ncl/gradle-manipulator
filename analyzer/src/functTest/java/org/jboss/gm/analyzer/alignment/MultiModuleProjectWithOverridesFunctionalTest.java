package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.internal.Pair;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.utils.FileUtils;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class MultiModuleProjectWithOverridesFunctionalTest
        extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public final TemporaryFolder tempDir = new TemporaryFolder();

    @SuppressWarnings("ConstantConditions")
    private final Map<String, String> dependencyOverrides = Stream
            .of(
                    Pair.of("dependencyOverride.junit:*@org.acme:subproject2", ""),
                    Pair.of("dependencyOverride.org.apache.commons:*@org.acme.subproject:*", "3.12.0.redhat-00002"),
                    Pair.of("dependencyOverride.io.netty:netty-*@*", "4.1.72.Final-redhat-00001"))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

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
                                        .withBody(readSampleDAResponse("multi-module-overrides-da-root.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse("multi-module-overrides-da-root-project.json"))));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun()
            throws IOException, URISyntaxException, XmlPullParserException, ManipulationException {

        System.setProperty("reportNonAligned", Boolean.TRUE.toString());
        System.setProperty(
                "reportTxtOutputFile",
                Configuration.REPORT_JSON_OUTPUT_FILE
                        .replaceFirst("\\.json$", ".txt"));

        final File projectRoot = tempDir.newFolder("multi-module-for-overrides");
        new File(projectRoot, "subproject1/subproject11").mkdirs();
        new File(projectRoot, "subproject2").mkdirs();
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot,
                projectRoot.getName(),
                dependencyOverrides);

        // Ensure we have only passed unique GAV to the REST API.
        assertThat(systemOutRule.getLinesNormalized()).contains(
                "Passing 9 GAVs into the REST client api [com.google.inject:guice:4.2.2, io.netty:netty:3.7.0.Final, "
                        + "io.netty:netty-buffer:4.1.68.Final, io.netty:netty-codec:4.1.68.Final, junit:junit:4.12, org.apache.commons:commons-lang3:3.8.1, "
                        + "org.hibernate:hibernate-core:5.4.2.Final, org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1, org.springframework:spring-context:5.1.6.RELEASE]");
        assertThat(systemOutRule.getLinesNormalized()).contains(
                "Updating sub-project org.acme:subproject1:null (path: "
                        + "subproject1) from version 1.1.2 to 1.1.2.redhat-00005");
        assertTrue(new File(projectRoot, AlignmentTask.GME).exists());
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
        assertEquals(
                AlignmentTask.INJECT_GME_END,
                FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.getVersion()).isEqualTo("1.1.2.redhat-00005");

            assertThat(am.getChildren().keySet()).hasSize(2).containsExactly("subproject1", "subproject2");

            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.1.2.redhat-00005");
                final Collection<ProjectVersionRef> alignedDependencies = root.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("netty-buffer", "4.1.72.Final-redhat-00001"),
                                tuple("netty-codec", "4.1.72.Final-redhat-00001"));
            });

            assertThat(am.findCorrespondingChild("subproject1")).satisfies(subproject1 -> {
                assertThat(subproject1.getChildren().keySet()).hasSize(1).containsExactly("subproject11");
                assertThat(subproject1.getVersion()).isEqualTo("1.1.2.redhat-00005");
                final Collection<ProjectVersionRef> alignedDependencies = subproject1.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("junit", "4.12.0.redhat-00001"),
                                tuple("netty-buffer", "4.1.72.Final-redhat-00001"),
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"),
                                tuple("hibernate-core", "5.4.2.Final-redhat-00001"));
            });

            assertThat(am.findCorrespondingChild("subproject2")).satisfies(subproject2 -> {
                assertThat(subproject2.getVersion()).isEqualTo("1.1.2.redhat-00005");
                final Collection<ProjectVersionRef> alignedDependencies = subproject2.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        // dependency on subproject1 should not be included
                        .containsOnly(
                                tuple("resteasy-jaxrs", "3.6.3.SP1-redhat-00001"));
            });

            assertThat(am.findCorrespondingChild(":subproject1:subproject11")).satisfies(subproject11 -> {
                assertThat(subproject11.getVersion()).isEqualTo("1.1.2.redhat-00005");
                final Collection<ProjectVersionRef> alignedDependencies = subproject11.getAlignedDependencies()
                        .values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("junit", "4.12.0.redhat-00001"),
                                tuple("commons-lang3", "3.12.0.redhat-00002"),
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"));
            });
        });

        // we care about how many calls are made to DA from an implementation perspective which is why we assert
        verify(1, postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS)));

        // check that generated settings.xml contains correct remote repositories
        File settingsFile = new File(projectRoot, "settings.xml");
        try (Reader reader = Files.newBufferedReader(settingsFile.toPath())) {
            SettingsXpp3Reader settingsXpp3Reader = new SettingsXpp3Reader();
            Settings generatedSettings = settingsXpp3Reader.read(reader);
            List<Repository> repositories = generatedSettings.getProfiles().get(0).getRepositories();

            assertThat(repositories).extracting("url")
                    // should not contain duplicate entries
                    .containsOnly(
                            "https://repo.maven.apache.org/maven2/",
                            "https://plugins.gradle.org/m2");
        }

        // make sure the project name was not changed
        List<String> settingsLines = org.apache.commons.io.FileUtils.readLines(
                new File(projectRoot, "settings.gradle"),
                Charset.defaultCharset());
        assertThat(settingsLines).map(String::trim)
                .filteredOn(s -> s.startsWith("rootProject.name"))
                .singleElement(as(STRING))
                .endsWith("'root'");
    }

    @Test
    public void verifyManipulationJSON() throws IOException, URISyntaxException {
        final Path projectRoot = tempDir.newFolder("multi-module-for-overrides").toPath();
        new File(projectRoot.toFile(), "subproject1/subproject11").mkdirs();
        new File(projectRoot.toFile(), "subproject2").mkdirs();
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot.toFile(),
                projectRoot.getFileName().toString(),
                dependencyOverrides);
        assertThat(alignmentModel).isNotNull();
        final Path jsonFile = projectRoot.resolve("manipulation.json");
        assertThat(jsonFile).isRegularFile().isReadable();
        final String jsonString = org.apache.commons.io.FileUtils.readFileToString(
                jsonFile.toFile(),
                StandardCharsets.UTF_8);
        System.out.println("JSON\n" + jsonString);
        final String expectedJsonString = String.format(
                "{%n"
                        + "  \"group\" : \"org.acme\",%n"
                        + "  \"name\" : \"root\",%n"
                        + "  \"projectPathName\" : \"root\",%n"
                        + "  \"version\" : \"1.1.2.redhat-00005\",%n"
                        + "  \"originalVersion\" : \"1.1.2\",%n"
                        + "  \"alignedDependencies\" : {%n"
                        + "    \"io.netty:netty-buffer:4.1.68.Final\" : {%n"
                        + "      \"groupId\" : \"io.netty\",%n"
                        + "      \"artifactId\" : \"netty-buffer\",%n"
                        + "      \"version\" : \"4.1.72.Final-redhat-00001\"%n"
                        + "    },%n"
                        + "    \"io.netty:netty-codec:4.1.68.Final\" : {%n"
                        + "      \"groupId\" : \"io.netty\",%n"
                        + "      \"artifactId\" : \"netty-codec\",%n"
                        + "      \"version\" : \"4.1.72.Final-redhat-00001\"%n"
                        + "    }%n" + "  },%n"
                        + "  \"children\" : {%n"
                        + "    \"subproject1\" : {%n"
                        + "      \"group\" : \"org.acme\",%n"
                        + "      \"name\" : \"subproject1\",%n"
                        + "      \"projectPathName\" : \"subproject1\",%n"
                        + "      \"version\" : \"1.1.2.redhat-00005\",%n"
                        + "      \"originalVersion\" : \"1.1.2\",%n"
                        + "      \"alignedDependencies\" : {%n"
                        + "        \"io.netty:netty-buffer:4.1.68.Final\" : {%n"
                        + "          \"groupId\" : \"io.netty\",%n"
                        + "          \"artifactId\" : \"netty-buffer\",%n"
                        + "          \"version\" : \"4.1.72.Final-redhat-00001\"%n"
                        + "        },%n"
                        + "        \"junit:junit:4.12\" : {%n"
                        + "          \"groupId\" : \"junit\",%n"
                        + "          \"artifactId\" : \"junit\",%n"
                        + "          \"version\" : \"4.12.0.redhat-00001\"%n"
                        + "        },%n"
                        + "        \"org.hibernate:hibernate-core:5.4.2.Final\" : {%n"
                        + "          \"groupId\" : \"org.hibernate\",%n"
                        + "          \"artifactId\" : \"hibernate-core\",%n"
                        + "          \"version\" : \"5.4.2.Final-redhat-00001\"%n"
                        + "        },%n"
                        + "        \"org.springframework:spring-context:5.1.6.RELEASE\" : {%n"
                        + "          \"groupId\" : \"org.springframework\",%n"
                        + "          \"artifactId\" : \"spring-context\",%n"
                        + "          \"version\" : \"5.1.6.RELEASE-redhat-00005\"%n"
                        + "        }%n"
                        + "      },%n"
                        + "      \"children\" : {%n"
                        + "        \"subproject11\" : {%n"
                        + "          \"group\" : \"org.acme.subproject\",%n"
                        + "          \"name\" : \"subproject11\",%n"
                        + "          \"projectPathName\" : \"subproject11\",%n"
                        + "          \"version\" : \"1.1.2.redhat-00005\",%n"
                        + "          \"originalVersion\" : \"1.1.2\",%n"
                        + "          \"alignedDependencies\" : {%n"
                        + "            \"junit:junit:4.12\" : {%n"
                        + "              \"groupId\" : \"junit\",%n"
                        + "              \"artifactId\" : \"junit\",%n"
                        + "              \"version\" : \"4.12.0.redhat-00001\"%n"
                        + "            },%n"
                        + "            \"org.apache.commons:commons-lang3:3.8.1\" : {%n"
                        + "              \"groupId\" : \"org.apache.commons\",%n"
                        + "              \"artifactId\" : \"commons-lang3\",%n"
                        + "              \"version\" : \"3.12.0.redhat-00002\"%n"
                        + "            },%n"
                        + "            \"org.springframework:spring-context:5.1.6.RELEASE\" : {%n"
                        + "              \"groupId\" : \"org.springframework\",%n"
                        + "              \"artifactId\" : \"spring-context\",%n"
                        + "              \"version\" : \"5.1.6.RELEASE-redhat-00005\"%n"
                        + "            }%n"
                        + "          }%n"
                        + "        }%n"
                        + "      }%n"
                        + "    },%n"
                        + "    \"subproject2\" : {%n"
                        + "      \"group\" : \"org.acme\",%n"
                        + "      \"name\" : \"subproject2\",%n"
                        + "      \"projectPathName\" : \"subproject2\",%n"
                        + "      \"version\" : \"1.1.2.redhat-00005\",%n"
                        + "      \"originalVersion\" : \"1.1.2\",%n"
                        + "      \"alignedDependencies\" : {%n"
                        + "        \"org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1\" : {%n"
                        + "          \"groupId\" : \"org.jboss.resteasy\",%n"
                        + "          \"artifactId\" : \"resteasy-jaxrs\",%n"
                        + "          \"version\" : \"3.6.3.SP1-redhat-00001\"%n"
                        + "        }%n"
                        + "      }%n"
                        + "    }%n"
                        + "  }%n"
                        + "}%n");
        assertThat(jsonString).isEqualTo(expectedJsonString);
    }
}
