package org.jboss.gm.analyzer.alignment;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;

public abstract class AbstractWiremockTest {

    @BeforeClass
    public static void beforeClass() {
        StdErrLog el = new StdErrLog();
        el.setLevel(10);
        Log.setLog(el);

        // Had strange behaviour where a single test would pass but multiple would fail.
        // Various suggestions in https://github.com/tomakehurst/wiremock/issues/132
        System.setProperty("http.keepAlive", "false");
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    @Before
    public void before() {
        System.out.println("Created with wiremock URL " + wireMockRule.baseUrl());
    }

    protected String readSampleDAResponse(String responseFileName) throws URISyntaxException, IOException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(responseFileName);

        if (resource == null) {
            throw new RuntimeException();
        }
        return FileUtils.readFileToString(
                Paths.get(resource.toURI()).toFile(),
                StandardCharsets.UTF_8.name());
    }

}
