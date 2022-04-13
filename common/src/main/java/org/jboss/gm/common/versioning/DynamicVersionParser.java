package org.jboss.gm.common.versioning;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;

import static org.apache.commons.lang.StringUtils.isEmpty;

public class DynamicVersionParser {

    private static final VersionSelectorScheme selector = new DefaultVersionSelectorScheme(new DefaultVersionComparator(),
            new VersionParser());

    public static boolean isDynamic(String version) {

        if (isEmpty(version)) {
            return false;
        }
        VersionSelector s = selector.parseSelector(version);

        switch (s.getClass().getName()) {
            case "org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector": {
                return false;
            }
            case "org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector":
            case "org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector":
            case "org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector": {
                return true;
            }
            default: {
                throw new ManipulationUncheckedException("Unknown version type for {}", s);
            }
        }
    }
}
