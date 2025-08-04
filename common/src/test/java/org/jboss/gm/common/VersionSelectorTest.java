package org.jboss.gm.common;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import org.jboss.gm.common.versioning.DynamicVersionParser;
import org.junit.Test;

public class VersionSelectorTest {

    @Test
    public void testSelector() {

        assertFalse(DynamicVersionParser.isDynamic(""));
        assertFalse(DynamicVersionParser.isDynamic("1.1.Final"));
        assertTrue(DynamicVersionParser.isDynamic("1.1+"));
        assertTrue(DynamicVersionParser.isDynamic("latest.release"));
        assertTrue(DynamicVersionParser.isDynamic("[1.0,)"));
    }
}
