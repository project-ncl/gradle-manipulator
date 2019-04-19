package org.jboss.gm.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.gm.common.alignment.AlignmentModel;

public final class TestUtils {

    private TestUtils() {
    }

    public static void copyDirectory(String classpathResource, File target) throws URISyntaxException, IOException {
        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(classpathResource).toURI()).toFile(), target);
    }

    static Tuple getAlignedTuple(AlignmentModel.Module alignment, String artifactId, String expected) {
        final List<String> versions = alignment.getAlignedDependencies().entrySet().stream()
                .filter(entry -> entry.getKey().contains(artifactId))
                .map(entry -> entry.getValue().getVersionString())
                .collect(Collectors.toList());
        final int count = versions.size();
        if (count != 1) {
            if (count == 0) {
                if (expected == null) {
                    throw new IllegalArgumentException("No alignment was found for " + artifactId
                            + " and needed an expected value to test against");
                }
                return tuple(artifactId, expected);
            }
            throw new IllegalArgumentException("Cannot retrieve a single version from " + artifactId
                    + ". Use more specific information");
        }

        return tuple(artifactId, versions.get(0));
    }

    static Tuple getAlignedTuple(AlignmentModel.Module alignment, String artifactId) {
        return getAlignedTuple(alignment, artifactId, null);
    }

    static Pair<Model, AlignmentModel.Module> getModelAndCheckGAV(File multiModuleRoot, AlignmentModel alignment,
            String generatedPomPathAsString)
            throws IOException,
            XmlPullParserException {
        final Path generatedPomPath = multiModuleRoot.toPath().resolve(generatedPomPathAsString);
        assertThat(generatedPomPath).isRegularFile();

        // find module
        final AlignmentModel.Module module;
        if (!generatedPomPathAsString.startsWith("build")) {
            final String moduleName = generatedPomPathAsString.substring(0, generatedPomPathAsString.indexOf('/'));
            module = alignment.getModules().stream()
                    .filter(m -> moduleName.equals(m.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown module: " + moduleName));
        } else {
            module = alignment.getModules().get(0);
        }

        final MavenXpp3Reader reader = new MavenXpp3Reader();
        final Model model = reader.read(new FileReader(generatedPomPath.toFile()));
        assertThat(model.getGroupId()).isEqualTo(alignment.getGroup());
        assertThat(model.getArtifactId()).isEqualTo(module.getName());
        assertThat(model.getVersion()).isEqualTo(module.getNewVersion());

        return new ImmutablePair<>(model, module);
    }
}
