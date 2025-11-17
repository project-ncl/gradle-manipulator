package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.gradlemanipulator.common.versioning.ProjectVersionFactory.withGAV;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.rules.LoggingRule;
import org.jboss.pnc.mavenmanipulator.core.state.DependencyState;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.jboss.pnc.mavenmanipulator.io.rest.RestException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

@RunWith(Parameterized.class)
public class DAAlignmentServiceWiremockTest {

    private static final int PORT = 8089;

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(PORT);

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.INFO);

    @Parameters
    public static Object[] data() {
        return new Object[] {
                DependencyState.DependencyPrecedence.REST,
                DependencyState.DependencyPrecedence.NONE,
        };
    }

    @Parameter
    public DependencyState.DependencyPrecedence precedence;

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse())));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAProjectResponse())));
    }

    @Test
    public void alignmentWorksAsExpected()
            throws RestException, IOException {
        System.setProperty(Configuration.DA, String.format("http://localhost:%d/da/rest/v-1", PORT));
        System.setProperty("dependencySource", precedence.toString());
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final DAAlignmentService sut = new DAAlignmentService(configuration);

        final ProjectVersionRef projectGav = withGAV("org.acme", "dummy", "1.0.0");
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0");
        final AlignmentService.Response response = sut.align(
                new AlignmentService.Request(
                        Collections.singletonList(projectGav),
                        Stream.of(
                                hibernateGav,
                                undertowGav,
                                mockitoGav).collect(Collectors.toList())));

        final File simpleProjectRoot = tempDir.newFolder("dummy");
        final Project project = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        project.setVersion("1.0.0");
        project.setGroup("org.acme");

        assertThat(response).isNotNull().satisfies(r -> {
            if (precedence != DependencyState.DependencyPrecedence.NONE) {
                assertThat(r.getAlignedVersionOfGav(project, hibernateGav)).isEqualTo("5.3.7.Final-redhat-00001");
                assertThat(r.getAlignedVersionOfGav(project, undertowGav)).isEqualTo("2.0.15.Final-redhat-00001");
                assertThat(r.getAlignedVersionOfGav(project, mockitoGav)).isNull();
            } else {
                assertThat(systemOutRule.getLinesNormalized()).contains(
                        "No dependencySource configured ; unable pass GAVs into endpoint");
            }
        });
    }

    private String readSampleDAResponse() throws URISyntaxException, IOException {
        return FileUtils.readFileToString(
                Paths.get(
                        DAAlignmentServiceWiremockTest.class.getClassLoader()
                                .getResource("sample-da-response.json")
                                .toURI())
                        .toFile(),
                StandardCharsets.UTF_8.name());
    }

    private String readSampleDAProjectResponse() throws URISyntaxException, IOException {
        return FileUtils.readFileToString(
                Paths.get(
                        DAAlignmentServiceWiremockTest.class.getClassLoader()
                                .getResource(
                                        "sample-da-response-project" +
                                                ".json")
                                .toURI())
                        .toFile(),
                StandardCharsets.UTF_8.name());
    }
}
