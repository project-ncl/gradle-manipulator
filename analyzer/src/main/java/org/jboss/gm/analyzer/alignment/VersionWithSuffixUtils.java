package org.jboss.gm.analyzer.alignment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling versions with suffixes
 *
 * @see org.jboss.gm.analyzer.alignment.UpdateProjectVersionCustomizer
 */
public final class VersionWithSuffixUtils {

    private VersionWithSuffixUtils() {
    }

    /**
     * Provides the next version. Some examples are:
     *
     * <pre>
     * {@code
     *  assert VersionWithSuffixUtils.getNextVersion("1.2.3", "acme", 3).equals("1.2.3-acme-001")
     *  assert VersionWithSuffixUtils.getNextVersion("1.2.3-acme-002", "acme", 3).equals("1.2.3-acme-003")
     *  assert VersionWithSuffixUtils.getNextVersion("3.1.0-Final", "org", 5).equals("3.1.0-Final-org-00001")
     *  assert VersionWithSuffixUtils.getNextVersion("3.1.0-Final-00009", "org", 5).equals("3.1.0-Final-org-00010")
     * }
     * </pre>
     */
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
