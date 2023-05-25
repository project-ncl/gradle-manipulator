package org.jboss.gm.manipulation;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.internal.Pair;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.internal.DefaultGradleRunner;
import org.jboss.gm.common.model.ManipulationModel;

import static org.apache.commons.lang3.StringUtils.equalsAny;
import static org.apache.commons.lang3.StringUtils.startsWithAny;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public final class TestUtils {

    private static final String[] PROPERTY_PREFIXES = { "java.", "user.", "sun." };

    private static final String[] OTHER_PROPERTIES = { "file.separator", "line.separator", "os.arch", "os.name", "os.version",
            "path.separator" };

    private TestUtils() {
    }

    static void copyDirectory(String classpathResource, File target) throws URISyntaxException, IOException {
        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(classpathResource).toURI()).toFile(), target);
    }

    static Tuple getAlignedTuple(ManipulationModel alignment, String artifactId, String expected) {
        final List<String> versions = alignment.getAlignedDependencies().entrySet().stream()
                .filter(entry -> entry.getKey().contains(artifactId))
                .map(entry -> entry.getValue().getVersionString())
                .collect(Collectors.toList());
        final int count = versions.size();
        if (count != 1) {
            if (count == 0) {
                if (expected == null) {
                    throw new IllegalArgumentException("No model was found for " + artifactId
                            + " and needed an expected value to test against");
                }
                return tuple(artifactId, expected);
            }
            throw new IllegalArgumentException("Cannot retrieve a single version from " + artifactId
                    + ". Use more specific information");
        }

        return tuple(artifactId, versions.get(0));
    }

    static Tuple getAlignedTuple(ManipulationModel alignment, String artifactId) {
        return getAlignedTuple(alignment, artifactId, null);
    }

    static Pair<Model, ManipulationModel> getModelAndCheckGAV(File parentLocationForPom, ManipulationModel alignment,
            String relativeGeneratedPomPathAsString)
            throws IOException,
            XmlPullParserException {

        return getModelAndCheckGAV(parentLocationForPom, alignment, relativeGeneratedPomPathAsString, false);
    }

    static Pair<Model, ManipulationModel> getModelAndCheckGAV(File parentLocationForPom, ManipulationModel alignment,
            String relativeGeneratedPomPathAsString, boolean external)
            throws IOException,
            XmlPullParserException {
        final Path generatedPomPath = parentLocationForPom.toPath().resolve(relativeGeneratedPomPathAsString);
        assertThat(generatedPomPath).isRegularFile();

        // find module
        final ManipulationModel module;
        if (!external && !relativeGeneratedPomPathAsString.startsWith("build")) {
            final String moduleName = relativeGeneratedPomPathAsString.substring(0,
                    relativeGeneratedPomPathAsString.indexOf('/'));
            module = alignment.getChildren().get(moduleName);
        } else {
            module = alignment;
        }

        try (final Reader reader = Files.newBufferedReader(generatedPomPath)) {
            final MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
            final Model model = mavenXpp3Reader.read(reader);
            assertThat(model.getGroupId()).isEqualTo(alignment.getGroup());
            assertThat(model.getArtifactId()).isEqualTo(module.getName());
            assertThat(model.getVersion()).isEqualTo(module.getVersion());

            return Pair.of(model, module);
        }
    }

    static GradleRunner createGradleRunner() {
        Set<String> newKeys = new HashSet<>(System.getProperties().stringPropertyNames());

        newKeys.removeIf(k -> startsWithAny(k, PROPERTY_PREFIXES) || equalsAny(k, OTHER_PROPERTIES));

        List<String> jvmArgs = new ArrayList<>(newKeys.size() + 1);

        for (String a : newKeys) {
            jvmArgs.add("-D" + a + '=' + System.getProperty(a));
        }

        // Used to indicate for the plugin to reinitialise the configuration whether in debug mode or not.
        System.setProperty("gmeFunctionalTest", "true");
        jvmArgs.add("-DgmeFunctionalTest=true");

        System.out.println("Will be using jvm args of " + jvmArgs);

        DefaultGradleRunner dgr = (DefaultGradleRunner) GradleRunner.create();
        dgr.withJvmArguments(jvmArgs);
        // Always run with debug for coverage
        // https://discuss.gradle.org/t/jacoco-gradle-test-kit-with-java/36603
        dgr.withDebug(true);
        dgr.forwardOutput();
        dgr.withPluginClasspath();

        return dgr;
    }
}
