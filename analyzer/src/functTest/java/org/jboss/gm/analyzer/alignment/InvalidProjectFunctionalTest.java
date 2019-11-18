package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

public class InvalidProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("invalid-project-da-response.json"))));

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    @Test
    public void ensureInvalidException() throws IOException, URISyntaxException {
        final File projectRoot = tempDir.newFolder("invalid-project");

        try {
            TestUtils.align(projectRoot, projectRoot.getName(), true);
            fail("No exception thrown");
        } catch (ManipulationUncheckedException e) {
            assertThat(e.getMessage().contains(
                    "For configuration compile; unable to resolve all dependencies: [io.undertow:undertow-core:2.0+, org.apache.commons:commons-lang3:3.8.1.redhat-3]"));
        }
    }
}
