package org.jboss.gm.analyzer.alignment;

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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.SystemErrRule;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

public abstract class AbstractWiremockTest {

    @ClassRule
    public static final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @BeforeClass
    public static void beforeClass() {
        StdErrLog el = new StdErrLog();
        el.setLevel(10);
        Log.setLog(el);
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
