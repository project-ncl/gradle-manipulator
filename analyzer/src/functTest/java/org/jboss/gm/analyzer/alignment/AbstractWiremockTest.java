package org.jboss.gm.analyzer.alignment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

public abstract class AbstractWiremockTest {

    static final int PORT = 8089;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

    protected String readSampleDAResponse(String responseFileName) {
        try {
            return FileUtils.readFileToString(
                    Paths.get(AbstractWiremockTest.class.getClassLoader().getResource(responseFileName)
                            .toURI()).toFile(),
                    StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
