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
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.Project;
import org.jboss.gm.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.utils.FileUtils;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.json.GAV;
import org.jboss.pnc.mavenmanipulator.common.json.ModulesItem;
import org.jboss.pnc.mavenmanipulator.common.json.PME;
import org.jboss.pnc.mavenmanipulator.common.util.JSONUtils;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class MultiModuleProjectFunctionalTest extends AbstractWiremockTest {

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
                                        .withBody(readSampleDAResponse("multi-module-da-root.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("multi-module-da-root-project.json"))));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun()
            throws IOException, URISyntaxException, XmlPullParserException, ManipulationException {

        final File projectRoot = tempDir.newFolder("multi-module");
        new File(projectRoot, "subproject1/subproject11").mkdirs();
        new File(projectRoot, "subproject2").mkdirs();
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName());

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
                assertThat(root.getAlignedDependencies()).isEmpty();
            });

            assertThat(am.findCorrespondingChild("subproject1")).satisfies(subproject1 -> {
                assertThat(subproject1.getChildren().keySet()).hasSize(1).containsExactly("subproject11");
                assertThat(subproject1.getVersion()).isEqualTo("1.1.2.redhat-00005");
                final Collection<ProjectVersionRef> alignedDependencies = subproject1.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
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
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"));
            });
        });

        assertThat(systemOutRule.getLinesNormalized()).contains("Attempting to disable alignment task in");

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
                            "https://maven.pkg.jetbrains.space/kotlin/p/kotlinx/maven",
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
    public void verifyAlignmentReportJson() throws IOException, URISyntaxException {
        final Path projectRoot = tempDir.newFolder("multi-module").toPath();
        new File(projectRoot.toFile(), "subproject1/subproject11").mkdirs();
        new File(projectRoot.toFile(), "subproject2").mkdirs();
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot.toFile(),
                projectRoot.getFileName().toString());
        assertThat(alignmentModel).isNotNull();
        final Path buildRoot = projectRoot.resolve("build");
        assertThat(buildRoot).isDirectory();
        final Path jsonFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE);
        assertThat(jsonFile).isRegularFile().isReadable();
        final Path textFile = buildRoot.resolve(
                Configuration.REPORT_JSON_OUTPUT_FILE
                        .replaceFirst("\\.json$", ".txt"));
        assertThat(textFile).doesNotExist();
        final PME jsonReport = JSONUtils.fileToJSON(jsonFile.toFile());
        final String jsonString = org.apache.commons.io.FileUtils.readFileToString(
                jsonFile.toFile(),
                StandardCharsets.UTF_8);
        final String expectedJsonString = String.format(
                "{%n" +
                        "  \"executionRoot\" : {%n" +
                        "    \"groupId\" : \"org.acme\",%n" +
                        "    \"artifactId\" : \"root\",%n" +
                        "    \"version\" : \"1.1.2.redhat-00005\",%n" +
                        "    \"originalGAV\" : \"org.acme:root:1.1.2\"%n" +
                        "  },%n" +
                        "  \"modules\" : [ {%n" +
                        "    \"gav\" : {%n" +
                        "      \"groupId\" : \"org.acme\",%n" +
                        "      \"artifactId\" : \"root\",%n" +
                        "      \"version\" : \"1.1.2.redhat-00005\",%n" +
                        "      \"originalGAV\" : \"org.acme:root:1.1.2\"%n" +
                        "    }%n" +
                        "  }, {%n" +
                        "    \"gav\" : {%n" +
                        "      \"groupId\" : \"org.acme\",%n" +
                        "      \"artifactId\" : \"subproject1\",%n" +
                        "      \"version\" : \"1.1.2.redhat-00005\",%n" +
                        "      \"originalGAV\" : \"org.acme:subproject1:1.1.2\"%n" +
                        "    },%n" +
                        "    \"dependencies\" : {%n" +
                        "      \"org.hibernate:hibernate-core:5.4.2.Final\" : {%n" +
                        "        \"groupId\" : \"org.hibernate\",%n" +
                        "        \"artifactId\" : \"hibernate-core\",%n" +
                        "        \"version\" : \"5.4.2.Final-redhat-00001\"%n" +
                        "      },%n" +
                        "      \"org.springframework:spring-context:5.1.6.RELEASE\" : {%n" +
                        "        \"groupId\" : \"org.springframework\",%n" +
                        "        \"artifactId\" : \"spring-context\",%n" +
                        "        \"version\" : \"5.1.6.RELEASE-redhat-00005\"%n" +
                        "      }%n" +
                        "    }%n" +
                        "  }, {%n" +
                        "    \"gav\" : {%n" +
                        "      \"groupId\" : \"org.acme\",%n" +
                        "      \"artifactId\" : \"subproject2\",%n" +
                        "      \"version\" : \"1.1.2.redhat-00005\",%n" +
                        "      \"originalGAV\" : \"org.acme:subproject2:1.1.2\"%n" +
                        "    },%n" +
                        "    \"dependencies\" : {%n" +
                        "      \"org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1\" : {%n" +
                        "        \"groupId\" : \"org.jboss.resteasy\",%n" +
                        "        \"artifactId\" : \"resteasy-jaxrs\",%n" +
                        "        \"version\" : \"3.6.3.SP1-redhat-00001\"%n" +
                        "      }%n" +
                        "    }%n" +
                        "  }, {%n" +
                        "    \"gav\" : {%n" +
                        "      \"groupId\" : \"org.acme\",%n" +
                        "      \"artifactId\" : \"subproject11\",%n" +
                        "      \"version\" : \"1.1.2.redhat-00005\",%n" +
                        "      \"originalGAV\" : \"org.acme:subproject11:1.1.2\"%n" +
                        "    },%n" +
                        "    \"dependencies\" : {%n" +
                        "      \"org.springframework:spring-context:5.1.6.RELEASE\" : {%n" +
                        "        \"groupId\" : \"org.springframework\",%n" +
                        "        \"artifactId\" : \"spring-context\",%n" +
                        "        \"version\" : \"5.1.6.RELEASE-redhat-00005\"%n" +
                        "      }%n" +
                        "    }%n" +
                        "  } ]%n" +
                        "}%n");
        assertThat(jsonString).isEqualTo(expectedJsonString);
        final GAV gav = new GAV();
        gav.setPVR(SimpleProjectVersionRef.parse("org.acme:root:1.1.2.redhat-00005"));
        assertThat(jsonReport.getGav().getGroupId()).isEqualTo(gav.getGroupId());
        assertThat(jsonReport.getGav().getArtifactId()).isEqualTo(gav.getArtifactId());
        assertThat(jsonReport.getGav().getVersion()).isEqualTo(gav.getVersion());
        assertThat(jsonReport.getGav().getOriginalGAV()).isEqualTo("org.acme:root:1.1.2");
        final List<ModulesItem> modules = jsonReport.getModules();
        assertThat(modules).hasSize(4);
        final ModulesItem module1 = modules.get(0);
        final ModulesItem module2 = modules.get(1);
        final ModulesItem module3 = modules.get(2);
        final ModulesItem module4 = modules.get(3);
        assertThat(module1.getGav()).isNotNull();
        assertThat(module1.getGav().getGroupId()).isEqualTo("org.acme");
        assertThat(module1.getGav().getArtifactId()).isEqualTo("root");
        assertThat(module1.getGav().getVersion()).isEqualTo("1.1.2.redhat-00005");
        assertThat(module1.getGav().getOriginalGAV()).isEqualTo("org.acme:root:1.1.2");
        assertThat(module2.getGav()).isNotNull();
        assertThat(module2.getGav().getGroupId()).isEqualTo("org.acme");
        assertThat(module2.getGav().getArtifactId()).isEqualTo("subproject1");
        assertThat(module2.getGav().getVersion()).isEqualTo("1.1.2.redhat-00005");
        assertThat(module2.getGav().getOriginalGAV()).isEqualTo("org.acme:subproject1:1.1.2");
        assertThat(module2.getDependencies()).hasSize(2)
                .containsEntry(
                        "org.hibernate:hibernate-core:5.4.2.Final",
                        SimpleProjectVersionRef.parse("org.hibernate:hibernate-core:5.4.2.Final-redhat-00001"))
                .containsEntry(
                        "org.springframework:spring-context:5.1.6.RELEASE",
                        SimpleProjectVersionRef.parse("org.springframework:spring-context:5.1.6.RELEASE-redhat-00005"));
        assertThat(module3.getGav()).isNotNull();
        assertThat(module3.getGav().getGroupId()).isEqualTo("org.acme");
        assertThat(module3.getGav().getArtifactId()).isEqualTo("subproject2");
        assertThat(module3.getGav().getVersion()).isEqualTo("1.1.2.redhat-00005");
        assertThat(module3.getGav().getOriginalGAV()).isEqualTo("org.acme:subproject2:1.1.2");
        assertThat(module3.getDependencies()).hasSize(1)
                .containsEntry(
                        "org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1",
                        SimpleProjectVersionRef.parse("org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1-redhat-00001"));
        assertThat(module4.getGav()).isNotNull();
        assertThat(module4.getGav().getGroupId()).isEqualTo("org.acme");
        assertThat(module4.getGav().getArtifactId()).isEqualTo("subproject11");
        assertThat(module4.getGav().getVersion()).isEqualTo("1.1.2.redhat-00005");
        assertThat(module4.getGav().getOriginalGAV()).isEqualTo("org.acme:subproject11:1.1.2");
        assertThat(module4.getDependencies()).hasSize(1)
                .containsEntry(
                        "org.springframework:spring-context:5.1.6.RELEASE",
                        SimpleProjectVersionRef.parse("org.springframework:spring-context:5.1.6.RELEASE-redhat-00005"));
        final String expectedTextString = String.format(
                "------------------- project org.acme:root (path: :)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "------------------- project org.acme:subproject1 (path: :subproject1)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.springframework:spring-context:5.1.6.RELEASE --> org.springframework:spring-context:5.1.6.RELEASE-redhat-00005%n"
                        +
                        "\tDependencies : org.hibernate:hibernate-core:5.4.2.Final --> org.hibernate:hibernate-core:5.4.2.Final-redhat-00001%n"
                        +
                        "%n" +
                        "------------------- project org.acme:subproject2 (path: :subproject2)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1 --> org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1-redhat-00001%n"
                        +
                        "%n" +
                        "------------------- project org.acme:subproject11 (path: :subproject1:subproject11)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.springframework:spring-context:5.1.6.RELEASE --> org.springframework:spring-context:5.1.6.RELEASE-redhat-00005%n"
                        +
                        "%n");
        assertThat(systemOutRule.getLinesNormalized()).contains(expectedTextString);
    }

    @Test
    public void verifyAlignmentReportText() throws IOException, URISyntaxException {
        System.setProperty("reportJSONOutputFile", "");
        final String reportJsonOutputFile = System.getProperty("reportJSONOutputFile");
        assertThat(reportJsonOutputFile).isNotNull().isEmpty();
        System.setProperty(
                "reportTxtOutputFile",
                Configuration.REPORT_JSON_OUTPUT_FILE
                        .replaceFirst("\\.json$", ".txt"));
        final String reportTxtOutputFile = System.getProperty("reportTxtOutputFile");
        assertThat(reportTxtOutputFile).isEqualTo(
                Configuration.REPORT_JSON_OUTPUT_FILE
                        .replaceFirst("\\.json$", ".txt"));
        final String reportNonAligned = System.getProperty("reportNonAligned");
        assertThat(reportNonAligned).isNull();
        final Path projectRoot = tempDir.newFolder("multi-module").toPath();
        new File(projectRoot.toFile(), "subproject1/subproject11").mkdirs();
        new File(projectRoot.toFile(), "subproject2").mkdirs();
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot.toFile(),
                projectRoot.getFileName().toString());
        assertThat(alignmentModel).isNotNull();
        final Path buildRoot = projectRoot.resolve("build");
        assertThat(buildRoot).isDirectory();
        final Path jsonFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE);
        assertThat(jsonFile).doesNotExist();
        final Path textFile = buildRoot.resolve(System.getProperty("reportTxtOutputFile"));
        assertThat(textFile).isRegularFile().isReadable();
        final String textString = org.apache.commons.io.FileUtils
                .readFileToString(textFile.toFile(), StandardCharsets.UTF_8);
        final String expectedTextString = String.format(
                "------------------- project org.acme:root (path: :)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "------------------- project org.acme:subproject1 (path: :subproject1)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.springframework:spring-context:5.1.6.RELEASE --> org.springframework:spring-context:5.1.6.RELEASE-redhat-00005%n"
                        +
                        "\tDependencies : org.hibernate:hibernate-core:5.4.2.Final --> org.hibernate:hibernate-core:5.4.2.Final-redhat-00001%n"
                        +
                        "%n" +
                        "------------------- project org.acme:subproject2 (path: :subproject2)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1 --> org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1-redhat-00001%n"
                        +
                        "%n" +
                        "------------------- project org.acme:subproject11 (path: :subproject1:subproject11)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.springframework:spring-context:5.1.6.RELEASE --> org.springframework:spring-context:5.1.6.RELEASE-redhat-00005%n"
                        +
                        "%n");
        assertThat(textString).isEqualTo(expectedTextString);
        assertThat(systemOutRule.getLinesNormalized()).contains(expectedTextString);
    }

    @Test
    public void verifyAlignmentReportTextReportNonAligned() throws IOException, URISyntaxException {
        System.setProperty("reportJSONOutputFile", "");
        final String reportJsonOutputFile = System.getProperty("reportJSONOutputFile");
        assertThat(reportJsonOutputFile).isNotNull().isEmpty();
        System.setProperty(
                "reportTxtOutputFile",
                Configuration.REPORT_JSON_OUTPUT_FILE
                        .replaceFirst("\\.json$", ".txt"));
        final String reportTxtOutputFile = System.getProperty("reportTxtOutputFile");
        assertThat(reportTxtOutputFile).isEqualTo(
                Configuration.REPORT_JSON_OUTPUT_FILE
                        .replaceFirst("\\.json$", ".txt"));
        System.setProperty("reportNonAligned", Boolean.TRUE.toString());
        final String reportNonAligned = System.getProperty("reportNonAligned");
        assertThat(reportNonAligned).isNotEmpty().isEqualTo(Boolean.TRUE.toString());
        final Path projectRoot = tempDir.newFolder("multi-module").toPath();
        new File(projectRoot.toFile(), "subproject1/subproject11").mkdirs();
        new File(projectRoot.toFile(), "subproject2").mkdirs();
        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot.toFile(),
                projectRoot.getFileName().toString());
        assertThat(alignmentModel).isNotNull();
        final Path buildRoot = projectRoot.resolve("build");
        assertThat(buildRoot).isDirectory();
        final Path jsonFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE);
        assertThat(jsonFile).doesNotExist();
        final Path textFile = buildRoot.resolve(reportTxtOutputFile);
        assertThat(textFile).isRegularFile().isReadable();
        final String textString = org.apache.commons.io.FileUtils
                .readFileToString(textFile.toFile(), StandardCharsets.UTF_8);
        final String expectedTextString = String.format(
                "------------------- project org.acme:root (path: :)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "------------------- project org.acme:subproject1 (path: :subproject1)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.springframework:spring-context:5.1.6.RELEASE --> org.springframework:spring-context:5.1.6.RELEASE-redhat-00005%n"
                        +
                        "\tDependencies : org.hibernate:hibernate-core:5.4.2.Final --> org.hibernate:hibernate-core:5.4.2.Final-redhat-00001%n"
                        +
                        "\tNon-Aligned Dependencies : junit:junit:4.12%n" +
                        "%n" +
                        "------------------- project org.acme:subproject2 (path: :subproject2)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1 --> org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1-redhat-00001%n"
                        +
                        "\tNon-Aligned Dependencies : junit:junit:4.12%n" +
                        "\tNon-Aligned Dependencies : com.google.inject:guice:4.2.2%n" +
                        "\tNon-Aligned Dependencies : org.apache.commons:commons-lang3:3.8.1%n" +
                        "%n" +
                        "------------------- project org.acme:subproject11 (path: :subproject1:subproject11)%n" +
                        "\tProject version : 1.1.2 --> 1.1.2.redhat-00005%n" +
                        "%n" +
                        "\tDependencies : org.springframework:spring-context:5.1.6.RELEASE --> org.springframework:spring-context:5.1.6.RELEASE-redhat-00005%n"
                        +
                        "\tNon-Aligned Dependencies : junit:junit:4.12%n" +
                        "\tNon-Aligned Dependencies : com.google.inject:guice:4.2.2%n" +
                        "\tNon-Aligned Dependencies : org.apache.commons:commons-lang3:3.8.1%n" +
                        "%n");
        assertThat(textString).isEqualTo(expectedTextString);
        assertThat(systemOutRule.getLinesNormalized()).contains(expectedTextString);
    }
}
