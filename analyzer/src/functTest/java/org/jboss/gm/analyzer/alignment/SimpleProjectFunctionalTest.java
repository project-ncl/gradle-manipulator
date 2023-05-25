package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.json.GAV;
import org.commonjava.maven.ext.common.json.ModulesItem;
import org.commonjava.maven.ext.common.json.PME;
import org.commonjava.maven.ext.common.util.JSONUtils;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.linesOf;
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
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-da-response.json"))));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("simple-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureAlignmentFileCreated() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot, projectRoot.getName(),
                Collections.singletonMap("dependencyOverride.com.yammer.metrics:*@org.acme.gradle:root", ""));

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + File.separator + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
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

        // Verify the org.gradle.caching has been removed.
        File properties = new File(projectRoot, "gradle.properties");
        assertThat(linesOf(properties)).anyMatch(value -> !value.contains("org.gradle.caching"));
        assertThat(FileUtils.readLines(properties, Charset.defaultCharset()))
                .filteredOn(l -> l.contains("org.gradle.caching")).hasSize(0);
    }

    @Test
    public void ensureAlignmentsFileOverwritten() throws IOException, URISyntaxException {
        final Path projectRoot = tempDir.newFolder("simple-project").toPath();
        final Path gme = projectRoot.resolve(AlignmentTask.GME);
        Files.createFile(gme);
        assertThat(gme).isEmptyFile();
        final Path gradleRoot = projectRoot.resolve(AlignmentTask.GRADLE);
        Files.createDirectory(gradleRoot);
        final Path gmeRepos = gradleRoot.resolve(AlignmentTask.GME_REPOS);
        Files.createFile(gmeRepos);
        assertThat(gmeRepos).isEmptyFile();
        final Path gmePluginconfigs = projectRoot.resolve(AlignmentTask.GME_PLUGINCONFIGS);
        Files.createFile(gmePluginconfigs);
        assertThat(gmePluginconfigs).isEmptyFile();
        TestUtils.align(projectRoot.toFile(), projectRoot.getFileName().toString(),
                Collections.singletonMap("dependencyOverride.com.yammer.metrics:*@org.acme.gradle:root", ""));
        assertThat(gme).isNotEmptyFile();
        assertThat(gmeRepos).isNotEmptyFile();
        assertThat(gmePluginconfigs).isNotEmptyFile();
    }

    @Test
    public void ensureAlignmentProduceCustomManipulationPlugin() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project");

        final TestManipulationModel alignmentModel = TestUtils.align(
                projectRoot, projectRoot.getName(),
                Collections.singletonMap("manipulationVersion", "0.1.2.3.4"));

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getOriginalVersion()).isEqualTo("1.0.1");
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
        });
        File gme = new File(projectRoot, AlignmentTask.GME);
        assertThat(gme).exists();
        assertThat(FileUtils.readFileToString(gme, Charset.defaultCharset()))
                .contains("org.jboss.gm:manipulation:0.1.2.3.4");
        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + '/' + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
        assertEquals(AlignmentTask.INJECT_GME_END,
                org.jboss.gm.common.utils.FileUtils.getLastLine(new File(projectRoot, Project.DEFAULT_BUILD_FILE)));

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

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root"))
                    .satisfies(root -> assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00002"));
        });

        // ensure we don't try to apply the manipulation plugin which in all likelihood isn't even available on the classpath
        TestUtils.createGradleRunner(projectRoot,
                Collections.singletonMap("org.gradle.project.gmeAnalyse", "true")).build();
        alignmentModel = new TestManipulationModel(ManipulationIO.readManipulationModel(projectRoot));
        List<String> lines = FileUtils.readLines(new File(projectRoot, Project.DEFAULT_BUILD_FILE), Charset.defaultCharset());

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme.gradle");
            assertThat(am.getName()).isEqualTo("root");
            assertThat(am.findCorrespondingChild("root")).satisfies(
                    root -> assertThat(root.getVersion()).isEqualTo("1.0.1.redhat-00003"));
        });
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));

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

        verify(postRequestedFor(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                .withRequestBody(notMatching(".*SNAPSHOT.*")));

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + '/' + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
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

    @Test
    public void verifyAlignmentReportJson() throws IOException, URISyntaxException {
        final Path projectRoot = tempDir.newFolder("simple-project").toPath();
        assertThat(projectRoot).isDirectory();
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot.toFile(),
                projectRoot.getFileName().toString());
        assertThat(alignmentModel).isNotNull();
        final Path buildRoot = projectRoot.resolve("build");
        assertThat(buildRoot).isDirectory();
        final Path jsonFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE);
        assertThat(jsonFile).isRegularFile().isReadable();
        final Path textFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE
                .replaceFirst("\\.json$", ".txt"));
        assertThat(textFile).doesNotExist();
        final PME jsonReport = JSONUtils.fileToJSON(jsonFile.toFile());
        final String jsonString = FileUtils.readFileToString(jsonFile.toFile(), StandardCharsets.UTF_8);
        final String expectedJsonString = String.format(
                "{%n" +
                        "  \"executionRoot\" : {%n" +
                        "    \"groupId\" : \"org.acme.gradle\",%n" +
                        "    \"artifactId\" : \"root\",%n" +
                        "    \"version\" : \"1.0.1.redhat-00002\",%n" +
                        "    \"originalGAV\" : \"org.acme.gradle:root:1.0.1\"%n" +
                        "  },%n" +
                        "  \"modules\" : [ {%n" +
                        "    \"gav\" : {%n" +
                        "      \"groupId\" : \"org.acme.gradle\",%n" +
                        "      \"artifactId\" : \"root\",%n" +
                        "      \"version\" : \"1.0.1.redhat-00002\",%n" +
                        "      \"originalGAV\" : \"org.acme.gradle:root:1.0.1\"%n" +
                        "    },%n" +
                        "    \"dependencies\" : {%n" +
                        "      \"io.undertow:undertow-core:2.0.15.Final\" : {%n" +
                        "        \"groupId\" : \"io.undertow\",%n" +
                        "        \"artifactId\" : \"undertow-core\",%n" +
                        "        \"version\" : \"2.0.15.Final-redhat-00001\"%n" +
                        "      },%n" +
                        "      \"org.hibernate:hibernate-core:5.3.7.Final\" : {%n" +
                        "        \"groupId\" : \"org.hibernate\",%n" +
                        "        \"artifactId\" : \"hibernate-core\",%n" +
                        "        \"version\" : \"5.3.7.Final-redhat-00001\"%n" +
                        "      },%n" +
                        "      \"com.yammer.metrics:metrics-core:2.2.0\" : {%n" +
                        "        \"groupId\" : \"com.yammer.metrics\",%n" +
                        "        \"artifactId\" : \"metrics-core\",%n" +
                        "        \"version\" : \"2.2.0-redhat-00001\"%n" +
                        "      }%n" +
                        "    }%n" +
                        "  } ]%n" +
                        "}%n");
        assertThat(jsonString).isEqualTo(expectedJsonString);
        final GAV gav = new GAV();
        gav.setPVR(SimpleProjectVersionRef.parse("org.acme.gradle:root:1.0.1.redhat-00002"));
        assertThat(jsonReport.getGav().getGroupId()).isEqualTo(gav.getGroupId());
        assertThat(jsonReport.getGav().getArtifactId()).isEqualTo(gav.getArtifactId());
        assertThat(jsonReport.getGav().getVersion()).isEqualTo(gav.getVersion());
        assertThat(jsonReport.getGav().getOriginalGAV()).isEqualTo("org.acme.gradle:root:1.0.1");
        final List<ModulesItem> modules = jsonReport.getModules();
        assertThat(modules).hasSize(1);
        final ModulesItem module1 = modules.get(0);
        assertThat(module1.getGav()).isNotNull();
        assertThat(module1.getGav().getGroupId()).isEqualTo("org.acme.gradle");
        assertThat(module1.getGav().getArtifactId()).isEqualTo("root");
        assertThat(module1.getGav().getVersion()).isEqualTo("1.0.1.redhat-00002");
        assertThat(module1.getGav().getOriginalGAV()).isEqualTo("org.acme.gradle:root:1.0.1");
        assertThat(module1.getDependencies()).hasSize(3)
                .containsEntry("com.yammer.metrics:metrics-core:2.2.0",
                        SimpleProjectVersionRef.parse("com.yammer.metrics:metrics-core:2.2.0-redhat-00001"))
                .containsEntry("io.undertow:undertow-core:2.0.15.Final",
                        SimpleProjectVersionRef.parse("io.undertow:undertow-core:2.0.15.Final-redhat-00001"))
                .containsEntry("org.hibernate:hibernate-core:5.3.7.Final",
                        SimpleProjectVersionRef.parse("org.hibernate:hibernate-core:5.3.7.Final-redhat-00001"));
        final String expectedTextString = String.format(
                "------------------- project org.acme.gradle:root (path: :)%n" +
                        "\tProject version : 1.0.1 --> 1.0.1.redhat-00002%n" +
                        "%n" +
                        "\tDependencies : org.hibernate:hibernate-core:5.3.7.Final --> org.hibernate:hibernate-core:5.3.7.Final-redhat-00001%n"
                        +
                        "\tDependencies : io.undertow:undertow-core:2.0.15.Final --> io.undertow:undertow-core:2.0.15.Final-redhat-00001%n"
                        +
                        "\tDependencies : com.yammer.metrics:metrics-core:2.2.0 --> com.yammer.metrics:metrics-core:2.2.0-redhat-00001%n"
                        +
                        "%n");
        assertThat(systemOutRule.getLog()).contains(expectedTextString);
    }

    @Test
    public void verifyAlignmentReportJsonVersionModificationDisabled() throws IOException, URISyntaxException {
        System.setProperty("versionModification", "false");
        final Path projectRoot = tempDir.newFolder("simple-project").toPath();
        assertThat(projectRoot).isDirectory();
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot.toFile(),
                projectRoot.getFileName().toString());
        assertThat(alignmentModel).isNotNull();
        final Path buildRoot = projectRoot.resolve("build");
        assertThat(buildRoot).isDirectory();
        final Path jsonFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE);
        assertThat(jsonFile).isRegularFile().isReadable();
        final Path textFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE
                .replaceFirst("\\.json$", ".txt"));
        assertThat(textFile).doesNotExist();
        final PME jsonReport = JSONUtils.fileToJSON(jsonFile.toFile());
        final String jsonString = FileUtils.readFileToString(jsonFile.toFile(), StandardCharsets.UTF_8);
        final String expectedJsonString = String.format(
                "{%n" +
                        "  \"executionRoot\" : {%n" +
                        "    \"groupId\" : \"org.acme.gradle\",%n" +
                        "    \"artifactId\" : \"root\",%n" +
                        "    \"version\" : \"1.0.1\",%n" +
                        "    \"originalGAV\" : \"org.acme.gradle:root:1.0.1\"%n" +
                        "  },%n" +
                        "  \"modules\" : [ {%n" +
                        "    \"gav\" : {%n" +
                        "      \"groupId\" : \"org.acme.gradle\",%n" +
                        "      \"artifactId\" : \"root\",%n" +
                        "      \"version\" : \"1.0.1\",%n" +
                        "      \"originalGAV\" : \"org.acme.gradle:root:1.0.1\"%n" +
                        "    },%n" +
                        "    \"dependencies\" : {%n" +
                        "      \"io.undertow:undertow-core:2.0.15.Final\" : {%n" +
                        "        \"groupId\" : \"io.undertow\",%n" +
                        "        \"artifactId\" : \"undertow-core\",%n" +
                        "        \"version\" : \"2.0.15.Final-redhat-00001\"%n" +
                        "      },%n" +
                        "      \"org.hibernate:hibernate-core:5.3.7.Final\" : {%n" +
                        "        \"groupId\" : \"org.hibernate\",%n" +
                        "        \"artifactId\" : \"hibernate-core\",%n" +
                        "        \"version\" : \"5.3.7.Final-redhat-00001\"%n" +
                        "      },%n" +
                        "      \"com.yammer.metrics:metrics-core:2.2.0\" : {%n" +
                        "        \"groupId\" : \"com.yammer.metrics\",%n" +
                        "        \"artifactId\" : \"metrics-core\",%n" +
                        "        \"version\" : \"2.2.0-redhat-00001\"%n" +
                        "      }%n" +
                        "    }%n" +
                        "  } ]%n" +
                        "}%n");
        assertThat(jsonString).isEqualTo(expectedJsonString);
        final GAV gav = new GAV();
        gav.setPVR(SimpleProjectVersionRef.parse("org.acme.gradle:root:1.0.1"));
        assertThat(jsonReport.getGav().getGroupId()).isEqualTo(gav.getGroupId());
        assertThat(jsonReport.getGav().getArtifactId()).isEqualTo(gav.getArtifactId());
        assertThat(jsonReport.getGav().getVersion()).isEqualTo(gav.getVersion());
        assertThat(jsonReport.getGav().getOriginalGAV()).isEqualTo("org.acme.gradle:root:1.0.1");
        final List<ModulesItem> modules = jsonReport.getModules();
        assertThat(modules).hasSize(1);
        final ModulesItem module1 = modules.get(0);
        assertThat(module1.getGav()).isNotNull();
        assertThat(module1.getGav().getGroupId()).isEqualTo("org.acme.gradle");
        assertThat(module1.getGav().getArtifactId()).isEqualTo("root");
        assertThat(module1.getGav().getVersion()).isEqualTo("1.0.1");
        assertThat(module1.getGav().getOriginalGAV()).isEqualTo("org.acme.gradle:root:1.0.1");
        assertThat(module1.getDependencies()).hasSize(3)
                .containsEntry("com.yammer.metrics:metrics-core:2.2.0",
                        SimpleProjectVersionRef.parse("com.yammer.metrics:metrics-core:2.2.0-redhat-00001"))
                .containsEntry("io.undertow:undertow-core:2.0.15.Final",
                        SimpleProjectVersionRef.parse("io.undertow:undertow-core:2.0.15.Final-redhat-00001"))
                .containsEntry("org.hibernate:hibernate-core:5.3.7.Final",
                        SimpleProjectVersionRef.parse("org.hibernate:hibernate-core:5.3.7.Final-redhat-00001"));
        final String expectedTextString = String.format(
                "------------------- project org.acme.gradle:root (path: :)%n" +
                        "%n" +
                        "\tDependencies : org.hibernate:hibernate-core:5.3.7.Final --> org.hibernate:hibernate-core:5.3.7.Final-redhat-00001%n"
                        +
                        "\tDependencies : io.undertow:undertow-core:2.0.15.Final --> io.undertow:undertow-core:2.0.15.Final-redhat-00001%n"
                        +
                        "\tDependencies : com.yammer.metrics:metrics-core:2.2.0 --> com.yammer.metrics:metrics-core:2.2.0-redhat-00001%n"
                        +
                        "%n");
        assertThat(systemOutRule.getLog()).contains(expectedTextString);
    }

    @Test
    public void verifyAlignmentReportText() throws IOException, URISyntaxException {
        System.setProperty("reportJSONOutputFile", "");
        final String reportJsonOutputFile = System.getProperty("reportJSONOutputFile");
        assertThat(reportJsonOutputFile).isNotNull().isEmpty();
        System.setProperty("reportTxtOutputFile", Configuration.REPORT_JSON_OUTPUT_FILE
                .replaceFirst("\\.json$", ".txt"));
        final String reportTxtOutputFile = System.getProperty("reportTxtOutputFile");
        assertThat(reportTxtOutputFile).isEqualTo(Configuration.REPORT_JSON_OUTPUT_FILE
                .replaceFirst("\\.json$", ".txt"));
        final String reportNonAligned = System.getProperty("reportNonAligned");
        assertThat(reportNonAligned).isNull();
        final Path projectRoot = tempDir.newFolder("simple-project").toPath();
        assertThat(projectRoot).isDirectory();
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot.toFile(),
                projectRoot.getFileName().toString());
        assertThat(alignmentModel).isNotNull();
        final Path buildRoot = projectRoot.resolve("build");
        assertThat(buildRoot).isDirectory();
        final Path jsonFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE);
        assertThat(jsonFile).doesNotExist();
        final Path textFile = buildRoot.resolve(System.getProperty("reportTxtOutputFile"));
        assertThat(textFile).isRegularFile().isReadable();
        final String textString = FileUtils.readFileToString(textFile.toFile(), StandardCharsets.UTF_8);
        final String expectedTextString = String.format(
                "------------------- project org.acme.gradle:root (path: :)%n" +
                        "\tProject version : 1.0.1 --> 1.0.1.redhat-00002%n" +
                        "%n" +
                        "\tDependencies : org.hibernate:hibernate-core:5.3.7.Final --> org.hibernate:hibernate-core:5.3.7.Final-redhat-00001%n"
                        +
                        "\tDependencies : io.undertow:undertow-core:2.0.15.Final --> io.undertow:undertow-core:2.0.15.Final-redhat-00001%n"
                        +
                        "\tDependencies : com.yammer.metrics:metrics-core:2.2.0 --> com.yammer.metrics:metrics-core:2.2.0-redhat-00001%n"
                        +
                        "%n");
        assertThat(textString).isEqualTo(expectedTextString);
        assertThat(systemOutRule.getLog()).contains(expectedTextString);
    }

    @Test
    public void verifyAlignmentReportTextReportNonAligned() throws IOException, URISyntaxException {
        System.setProperty("reportJSONOutputFile", "");
        final String reportJsonOutputFile = System.getProperty("reportJSONOutputFile");
        assertThat(reportJsonOutputFile).isNotNull().isEmpty();
        System.setProperty("reportTxtOutputFile", Configuration.REPORT_JSON_OUTPUT_FILE
                .replaceFirst("\\.json$", ".txt"));
        final String reportTxtOutputFile = System.getProperty("reportTxtOutputFile");
        assertThat(reportTxtOutputFile).isEqualTo(Configuration.REPORT_JSON_OUTPUT_FILE
                .replaceFirst("\\.json$", ".txt"));
        System.setProperty("reportNonAligned", Boolean.TRUE.toString());
        final String reportNonAligned = System.getProperty("reportNonAligned");
        assertThat(reportNonAligned).isNotEmpty().isEqualTo(Boolean.TRUE.toString());
        final Path projectRoot = tempDir.newFolder("simple-project").toPath();
        assertThat(projectRoot).isDirectory();
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot.toFile(),
                projectRoot.getFileName().toString());
        assertThat(alignmentModel).isNotNull();
        final Path buildRoot = projectRoot.resolve("build");
        assertThat(buildRoot).isDirectory();
        final Path jsonFile = buildRoot.resolve(Configuration.REPORT_JSON_OUTPUT_FILE);
        assertThat(jsonFile).doesNotExist();
        final Path textFile = buildRoot.resolve(reportTxtOutputFile);
        assertThat(textFile).isRegularFile().isReadable();
        final String textString = FileUtils.readFileToString(textFile.toFile(), StandardCharsets.UTF_8);
        final String expectedTextString = String.format(
                "------------------- project org.acme.gradle:root (path: :)%n" +
                        "\tProject version : 1.0.1 --> 1.0.1.redhat-00002%n" +
                        "%n" +
                        "\tDependencies : org.hibernate:hibernate-core:5.3.7.Final --> org.hibernate:hibernate-core:5.3.7.Final-redhat-00001%n"
                        +
                        "\tDependencies : io.undertow:undertow-core:2.0.15.Final --> io.undertow:undertow-core:2.0.15.Final-redhat-00001%n"
                        +
                        "\tDependencies : com.yammer.metrics:metrics-core:2.2.0 --> com.yammer.metrics:metrics-core:2.2.0-redhat-00001%n"
                        +
                        "\tNon-Aligned Dependencies : org.apache.commons:commons-lang3:3.8.1%n" +
                        "\tNon-Aligned Dependencies : junit:junit:4.12%n" +
                        "%n");
        assertThat(textString).isEqualTo(expectedTextString);
        assertThat(systemOutRule.getLog()).contains(expectedTextString);
    }

    @Test
    public void verifyOverrideHandling() throws IOException, URISyntaxException, ManipulationException {
        final File projectRoot = tempDir.newFolder("simple-project");
        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(projectRoot.getName()).toURI()).toFile(), projectRoot);
        File props = new File(projectRoot, "gradle.properties");
        FileUtils.writeStringToFile(props,
                FileUtils.readFileToString(props, Charset.defaultCharset()).replace(
                        "version=1.0.1", "version=0.1"),
                Charset.defaultCharset());

        final Map<String, String> gmeProps = Collections.singletonMap("versionOverride", "1.0.1");
        final TestManipulationModel alignmentModel = TestUtils.align(projectRoot, projectRoot.getName(), gmeProps);

        assertThat(new File(projectRoot, AlignmentTask.GME)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GRADLE + '/' + AlignmentTask.GME_REPOS)).exists();
        assertThat(new File(projectRoot, AlignmentTask.GME_PLUGINCONFIGS)).exists();
        assertEquals(AlignmentTask.INJECT_GME_START + " }", TestUtils.getLine(projectRoot));
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
