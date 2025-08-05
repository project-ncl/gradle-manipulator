package org.jboss.gm.manipulation;

import static junit.framework.TestCase.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;

public class ManipulationPluginTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Test
    public void verifyPluginLog() {
        new ManipulationPlugin();
        assertTrue(systemOutRule.getLinesNormalized().contains("Running Gradle Manipulation Plugin"));
    }

}
