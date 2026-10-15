package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Functional tests verifying that repositories injected by init scripts are excluded from the
 * generated {@code repositories-backup.xml} (a.k.a. {@code settings.xml}).
 */
public class InitGradleRepositoryFilteringFunctionalTest extends AbstractWiremockTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "init-gradle-repo-filtering-da-response.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(
                                                readSampleDAResponse(
                                                        "init-gradle-repo-filtering-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
        System.setProperty("gmeFunctionalTest", "true");
    }

    /**
     * Verifies that repositories injected by an {@code init.gradle} script are NOT written to
     * {@code repositories-backup.xml}, while repositories declared in the project's own
     * {@code build.gradle} ARE written.
     *
     * <p>
     * The test runs {@code generateAlignmentMetadata} against the
     * {@code init-gradle-test/test-repo-filtering} project with the
     * {@code init.gradle} applied via {@code --init-script}. That init script injects:
     * <ul>
     * <li>{@code artifactory-mvn-plugins} (url {@code file:///dev/null}) — must be excluded</li>
     * <li>{@code gradlePluginPortal()} — must be excluded</li>
     * </ul>
     * The project itself declares {@code mavenCentral()} — must be included.
     */
    @Test
    public void initGradleRepositoriesAreExcludedFromSettingsXml()
            throws IOException, URISyntaxException, XmlPullParserException {

        File projectRoot = tempFolder.newFolder("test-repo-filtering");
        File resourceDir = Paths.get(
                InitGradleRepositoryFilteringFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/test-repo-filtering")
                        .toURI())
                .toFile();
        FileUtils.copyDirectory(resourceDir, projectRoot);

        File initGradle = Paths.get(
                InitGradleRepositoryFilteringFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/init.gradle")
                        .toURI())
                .toFile();
        File initGradleCopy = new File(projectRoot, "init.gradle");
        FileUtils.copyFile(initGradle, initGradleCopy);

        List<String> arguments = new ArrayList<>();
        arguments.add("--init-script");
        arguments.add(initGradleCopy.getAbsolutePath());
        arguments.add("--stacktrace");
        arguments.add("--info");
        arguments.add(AlignmentTask.NAME);
        arguments.add("-DgmeFunctionalTest=true");
        arguments.add("-DrepoRemovalBackup=settings.xml");
        arguments.add("-D" + Configuration.DA + "=http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");

        BuildResult buildResult = GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments(arguments)
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build();

        System.out.println(buildResult.getOutput());

        // Read and assert the generated settings.xml
        File settingsFile = new File(projectRoot, "settings.xml");
        assertThat(settingsFile).exists();

        try (Reader reader = Files.newBufferedReader(settingsFile.toPath())) {
            SettingsXpp3Reader settingsXpp3Reader = new SettingsXpp3Reader();
            Settings generatedSettings = settingsXpp3Reader.read(reader);
            List<Repository> repositories = generatedSettings.getProfiles().get(0).getRepositories();

            List<String> exportedUrls = new ArrayList<>();
            for (Repository r : repositories) {
                exportedUrls.add(r.getUrl());
            }

            // init.gradle injects artifactory-mvn-plugins (file:///dev/null) — must be excluded
            assertThat(exportedUrls)
                    .as("init.gradle-injected 'artifactory-mvn-plugins' repo must not appear in settings.xml")
                    .doesNotContain("file:///dev/null");

            // init.gradle calls gradlePluginPortal() — must be excluded (repository-driver
            // unconditionally adds its own gradle-plugins remote for every Gradle build).
            // Gradle normalises the URL without a trailing slash at runtime.
            assertThat(exportedUrls)
                    .as("gradlePluginPortal() must not appear in settings.xml")
                    .doesNotContain("https://plugins.gradle.org/m2")
                    .doesNotContain("https://plugins.gradle.org/m2/");

            // The project's own mavenCentral() must be preserved
            assertThat(exportedUrls)
                    .as("project-declared mavenCentral() must appear in settings.xml")
                    .contains("https://repo.maven.apache.org/maven2/");
        }
    }
}
