package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class InvalidProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("invalid-project-da-response.json"))));
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody(readSampleDAResponse("invalid-project-da-response-project.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureInvalidException() throws IOException, URISyntaxException {
        final File projectRoot = tempDir.newFolder("invalid-project");

        try {
            TestUtils.align(projectRoot, projectRoot.getName(), true);
            fail("No exception thrown");
        } catch (ManipulationUncheckedException e) {
            String regex = ".*For configuration compileClasspath, unable to resolve all project dependencies: \\[(.*)].*";
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(e.getMessage());
            assertThat(matcher.matches()).isTrue();
            assertThat(matcher.groupCount()).isEqualTo(1);
            String[] dependencies = matcher.group(1).split(", ");
            String[] expectedDependencies = {
                    "io.undertow:undertow-core:2.0+",
                    "org.apache.commons:commons-lang3:3.8.1.redhat-3",
                    "org.springframework:spring-context:5.1.6.INVALID_VERSION" };
            assertThat(dependencies).containsExactly(expectedDependencies);
        }
    }
}
