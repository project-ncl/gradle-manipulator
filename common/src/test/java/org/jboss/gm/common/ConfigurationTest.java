package org.jboss.gm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import org.aeonbits.owner.ConfigFactory;
import org.commonjava.maven.ext.io.rest.Translator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TestRule;

public class ConfigurationTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void verifySystem1() {
        System.setProperty("TEST", "VALUE");

        Configuration c = ConfigFactory.create(Configuration.class);

        assertEquals("VALUE", c.getProperty("TEST"));
    }

    @Test
    public void verifySystem2() {
        String deploy = "http://indy-stable-next.newcastle-devel.svc.cluster.local";
        System.setProperty("AProxDeployUrl", deploy);
        System.setProperty("manipulation.disable", "true");

        Configuration c = ConfigFactory.create(Configuration.class);

        assertEquals(deploy, c.deployUrl());
        assertTrue(c.disableGME());
    }

    @Test
    public void verifyEnv() {

        String deploy = "http://indy-stable-next.newcastle-devel.svc.cluster.local";
        environmentVariables.set("AProxDeployUrl", deploy);

        Configuration c = ConfigFactory.create(Configuration.class);

        assertEquals(deploy, c.deployUrl());

        assertEquals(Translator.RETRY_DURATION_SEC, c.restRetryDuration());
    }

    @Test
    public void verifyHeaders() {

        String deploy = "log-user-id:102,log-request-context:061294ff-088,log-process-context:,log-expires:,log-tmp:";
        environmentVariables.set("restHeaders", deploy);

        Configuration c = ConfigFactory.create(Configuration.class);

        Map<String, String> r = c.restHeaders();

        assertEquals(5, r.size());
    }

    @Test
    public void verifyDefaultState() {
        Configuration c = ConfigFactory.create(Configuration.class);
        assertNull(c.overrideTransitive());
    }

    @Test
    public void verifyDumpCurrentConfig() {
        final Configuration c = ConfigFactory.create(Configuration.class);
        final String currentConfig = c.dumpCurrentConfig();
        final String lineEnding = "(\\u000D\\u000A|[\\u000A\\u000D])";
        final String keyValue = "(\\t(.*) : (.*))" + lineEnding;
        final Pattern p = Pattern.compile("^" + lineEnding + "(" + keyValue + ")*$");
        assertThat(currentConfig).matches(p);
        final String[] lines = currentConfig.split(System.lineSeparator());
        assertThat(lines).isNotEmpty();
        final String[] keys = Arrays.stream(lines).map(line -> line.split(":")[0].trim()).toArray(String[]::new);
        assertThat(keys).hasSize(lines.length).isSorted();
    }
}
