package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public final class TestUtils {

    private TestUtils() {
    }

    static TestManipulationModel align(File projectRoot, String projectDirName) throws IOException, URISyntaxException {
        return align(projectRoot, projectDirName, new HashMap<>());
    }

    static TestManipulationModel align(File projectRoot, String projectDirName, Map<String, String> systemProps)
            throws IOException, URISyntaxException {
        return align(projectRoot, projectDirName, false, systemProps);
    }

    static TestManipulationModel align(File projectRoot, String projectDirName, boolean expectFailure)
            throws IOException, URISyntaxException {

        return align(projectRoot, projectDirName, expectFailure, Collections.emptyMap());
    }

    static TestManipulationModel align(File projectRoot, boolean expectFailure) {
        if (!new File(projectRoot, "build.gradle").exists() && !new File(projectRoot, "build.gradle.kts").exists()) {
            throw new ManipulationUncheckedException("No valid test directory structure");
        }
        return align(projectRoot, expectFailure, Collections.emptyMap());
    }

    static TestManipulationModel align(File projectRoot, String projectDirName, boolean expectFailure,
            Map<String, String> systemProps)
            throws IOException, URISyntaxException {

        URL resource = TestUtils.class.getClassLoader().getResource(projectDirName);
        assertThat(resource).isNotNull();
        FileUtils.copyDirectory(Paths.get(resource.toURI()).toFile(), projectRoot);
        return align(projectRoot, expectFailure, systemProps);
    }

    /**
     * Creates a new Gradle runner with the given project root and system properties.
     *
     * @param projectRoot the root project of the build under test
     * @param systemProps the system properties for the alignment run. If the property keys are not
     *        prefixed with -D or -P then a -D will be automatically added.
     * @return the new Gradle runner
     */
    static GradleRunner createGradleRunner(File projectRoot, Map<String, String> systemProps) {
        final Map<String, String> finalSystemProps = new LinkedHashMap<>(systemProps.size() + 1);
        if (!systemProps.containsKey("repoRemovalBackup")) {
            finalSystemProps.put("repoRemovalBackup", "settings.xml");
        }
        finalSystemProps.putAll(systemProps);
        final List<String> systemPropsList = finalSystemProps.entrySet().stream()
                .map(e -> (e.getKey().startsWith("-") ? e.getKey() : "-D" + e.getKey()) + "=" + e.getValue())
                .collect(Collectors.toList());
        final List<String> allArguments = new ArrayList<>(systemPropsList.size() + 4);
        // Used to indicate for the plugin to reinitialise the configuration whether in debug mode or not.
        System.setProperty("gmeFunctionalTest", "true");
        allArguments.add("-DgmeFunctionalTest=true");
        allArguments.add("--stacktrace");
        allArguments.add("--info");
        allArguments.add(AlignmentTask.NAME);
        allArguments.addAll(systemPropsList);

        return GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments(allArguments)
                // Always run with debug for coverage
                // https://discuss.gradle.org/t/jacoco-gradle-test-kit-with-java/36603
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath();
    }

    /**
     * This method assumes the projectRoot directory already contains the gradle files (usually unpacked from
     * resources).
     *
     * @param projectRoot the root directory of the aligned project
     * @param expectFailure if the alignment should fail
     * @param systemProps the system properties to apply for the alignment run
     * @return the manipulation model
     */
    static TestManipulationModel align(File projectRoot, boolean expectFailure, Map<String, String> systemProps) {
        assertTrue(projectRoot.toPath().resolve("build.gradle").toFile().exists() ||
                projectRoot.toPath().resolve("build.gradle.kts").toFile().exists());

        final GradleRunner runner = createGradleRunner(projectRoot, systemProps);
        final BuildResult buildResult;
        final TaskOutcome outcome;

        if (expectFailure) {
            buildResult = runner.buildAndFail();
            outcome = TaskOutcome.FAILED;
        } else {
            outcome = TaskOutcome.SUCCESS;
            buildResult = runner.build();
        }

        final BuildTask task = buildResult.task(":" + AlignmentTask.NAME);

        assertThat(task).isNotNull();
        assertThat(task.getOutcome()).isEqualTo(outcome);

        if (expectFailure) {
            throw new ManipulationUncheckedException(buildResult.getOutput());
        } else {
            if (!projectRoot.exists()) {
                throw new ManipulationUncheckedException("No manipulation file found");
            }
            return new TestManipulationModel(ManipulationIO.readManipulationModel(projectRoot));
        }
    }

    public static String getLine(File projectRoot) throws IOException, ManipulationException {

        File buildFile = new File(projectRoot, Project.DEFAULT_BUILD_FILE);
        if (!buildFile.exists()) {
            buildFile = new File(projectRoot, "build.gradle.kts");
        }
        List<String> lines = FileUtils.readLines(buildFile, Charset.defaultCharset());

        return org.jboss.gm.common.utils.FileUtils.getFirstLine(lines);
    }

    public static class TestManipulationModel extends ManipulationModel {

        public TestManipulationModel(ManipulationModel m) {
            group = m.getGroup();
            name = m.getName();
            version = m.getVersion();
            alignedDependencies = m.getAlignedDependencies();
            children = m.getChildren();
            setOriginalVersion(m.getOriginalVersion());
        }

        /**
         * Calls the super method but with less access protection for test purposes
         *
         * @param path string name
         * @return the child model
         */
        public ManipulationModel findCorrespondingChild(String path) {
            return super.findCorrespondingChild(path);
        }
    }
}
