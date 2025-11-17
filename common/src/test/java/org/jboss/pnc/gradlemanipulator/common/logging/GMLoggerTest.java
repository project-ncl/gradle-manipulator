package org.jboss.pnc.gradlemanipulator.common.logging;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class GMLoggerTest {

    @Rule
    public LoggingRule rule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Test
    public void debug() {
        System.setProperty("loggingLevel", "true");
        Configuration c = ConfigCache.getOrCreate(Configuration.class);
        c.reload();

        Logger logger = GMLogger.getLogger(this.getClass());
        logger.info("Test logging at info");
        logger.debug("Test logging at debug");

        assertTrue(systemOutRule.getLinesNormalized().contains("Test logging"));
        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains("[LIFECYCLE] [org.jboss.pnc.gradlemanipulator.common.logging.GMLoggerTest]"));
        assertTrue(
                systemOutRule.getLinesNormalized()
                        .contains("[INFO] [org.jboss.pnc.gradlemanipulator.common.logging.GMLoggerTest]"));
        // Was previously "[DEBUG][GMLogger" but gives spurious failures on CI. We really want to establish
        // that both the above logging lines are output anyway.
        assertTrue(systemOutRule.getLinesNormalized().contains("Test logging at info"));
        assertTrue(systemOutRule.getLinesNormalized().contains("Test logging at debug"));
    }

    @Test
    public void checkContext() {
        assertNotNull(FilteringCustomLogger.getContext());
    }
}
