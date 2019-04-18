package org.jboss.gm.analyzer.alignment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionWithSuffixUtils {

    private VersionWithSuffixUtils() {
    }

    public static String getNextVersion(String originalVersion, String suffix, int paddingCount) {
        final String regex = "(.*)-" + suffix + "-(\\d+)";
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(originalVersion);
        if (matcher.find()) {
            final String versionWithAnySuffix = matcher.group(1);
            final int currentSuffixVersion = Integer.valueOf(matcher.group(2));

            return createNewVersion(versionWithAnySuffix, suffix, paddingCount, currentSuffixVersion + 1);
        } else {
            // TODO: are there any other cases that need to be checked?
            return createNewVersion(originalVersion, suffix, paddingCount, 1);
        }
    }

    private static String createNewVersion(String nonPaddedVersion, String suffix, int paddingCount, int paddingVersion) {
        final String paddingFormat = "%0" + paddingCount + "d";
        return String.format("%s-%s-" + paddingFormat, nonPaddedVersion, suffix, paddingVersion);
    }

}
