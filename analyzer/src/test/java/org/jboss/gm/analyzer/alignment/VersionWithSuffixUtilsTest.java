package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class VersionWithSuffixUtilsTest {

    @Test
    public void testVersionsWithMatchingSuffix() {
        assertThat(VersionWithSuffixUtils.getNextVersion("5.3.7.Final-redhat-00009", "redhat", 5))
                .isEqualTo("5.3.7.Final-redhat-00010");
    }

    @Test
    public void testVersionsWithoutSuffix() {
        assertThat(VersionWithSuffixUtils.getNextVersion("5.0.2.RELEASE", "redhat", 5))
                .isEqualTo("5.0.2.RELEASE-redhat-00001");
    }

}