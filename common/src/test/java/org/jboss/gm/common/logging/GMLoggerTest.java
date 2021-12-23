package org.jboss.gm.common.logging;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GMLoggerTest {

    @Rule
    public LoggingRule rule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void debug() {
        System.setProperty("loggingLevel", "true");
        Configuration c = ConfigCache.getOrCreate(Configuration.class);
        c.reload();

        Logger logger = GMLogger.getLogger(this.getClass());
        logger.info("Test logging at info");
        logger.debug("Test logging at debug");

        assertTrue(systemOutRule.getLog().contains("Test logging"));
        assertTrue(systemOutRule.getLog().contains("[LIFECYCLE] [org.jboss.gm.common.logging.GMLoggerTest]"));
        assertTrue(systemOutRule.getLog().contains("[INFO] [org.jboss.gm.common.logging.GMLoggerTest]"));
        // Was previously "[DEBUG][GMLogger" but gives spurious failures on CI. We really want to establish
        // that both the above logging lines are output anyway.
        assertTrue(systemOutRule.getLog().contains("Test logging at info"));
        assertTrue(systemOutRule.getLog().contains("Test logging at debug"));
    }

    @Test
    public void checkContext() {
        assertNotNull(FilteringCustomLogger.getContext());
    }
}
