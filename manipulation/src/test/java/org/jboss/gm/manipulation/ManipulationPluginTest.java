package org.jboss.gm.manipulation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static junit.framework.TestCase.assertTrue;

public class ManipulationPluginTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void verifyPluginLog() {
        new ManipulationPlugin();
        assertTrue(systemOutRule.getLog().contains("Running Gradle Manipulation Plugin"));
    }

}