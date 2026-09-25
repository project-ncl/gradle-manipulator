package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InitGradleFunctionalTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void allprojectsHookPrependsMirrorFirst() throws Exception {
        File projectRoot = tempFolder.newFolder("test-allprojects");
        File resourceDir = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/test-allprojects")
                        .toURI())
                .toFile();
        FileUtils.copyDirectory(resourceDir, projectRoot);

        File initGradle = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/init.gradle")
                        .toURI())
                .toFile();
        File initGradleCopy = new File(projectRoot, "init.gradle");
        FileUtils.copyFile(initGradle, initGradleCopy);

        BuildResult buildResult = GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments("--init-script", initGradleCopy.getAbsolutePath(), "printRepos", "--quiet")
                .forwardOutput()
                .build();

        assertThat(buildResult.getOutput()).contains("REPO[0]: artifactory-mvn-plugins");
    }

    @Test
    public void beforeSettingsHookPrependsMirrorFirstForPluginManagement() throws Exception {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("6.0")) >= 0);

        File projectRoot = tempFolder.newFolder("test-pluginmanagement");
        File resourceDir = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/test-pluginmanagement")
                        .toURI())
                .toFile();
        FileUtils.copyDirectory(resourceDir, projectRoot);

        File initGradle = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/init.gradle")
                        .toURI())
                .toFile();
        File probeGradle = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/probe.gradle")
                        .toURI())
                .toFile();
        File initGradleCopy = new File(projectRoot, "init.gradle");
        File probeGradleCopy = new File(projectRoot, "probe.gradle");
        FileUtils.copyFile(initGradle, initGradleCopy);
        FileUtils.copyFile(probeGradle, probeGradleCopy);

        BuildResult buildResult = GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments(
                        "--init-script",
                        initGradleCopy.getAbsolutePath(),
                        "--init-script",
                        probeGradleCopy.getAbsolutePath(),
                        "help",
                        "--quiet")
                .forwardOutput()
                .build();

        System.out.print(buildResult.getOutput());
        assertThat(buildResult.getOutput()).contains("MGMT[0]: artifactory-mvn-plugins");
    }

    @Test
    public void beforeSettingsHookPrependsMirrorFirstForDependencyResolutionManagement() throws Exception {
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("6.8")) >= 0);

        File projectRoot = tempFolder.newFolder("test-drm");
        File resourceDir = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/test-drm")
                        .toURI())
                .toFile();
        FileUtils.copyDirectory(resourceDir, projectRoot);

        File initGradle = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/init.gradle")
                        .toURI())
                .toFile();
        File probeGradle = Paths.get(
                InitGradleFunctionalTest.class.getClassLoader()
                        .getResource("init-gradle-test/probe.gradle")
                        .toURI())
                .toFile();
        File initGradleCopy = new File(projectRoot, "init.gradle");
        File probeGradleCopy = new File(projectRoot, "probe.gradle");
        FileUtils.copyFile(initGradle, initGradleCopy);
        FileUtils.copyFile(probeGradle, probeGradleCopy);

        // DRM repositories must be read at settings evaluation time via probe.gradle, not from
        // project.repositories at task execution time — project.repositories only reflects the
        // allprojects hook injection, not the DRM list, so task-time iteration is a false signal.
        BuildResult buildResult = GradleRunner.create()
                .withProjectDir(projectRoot)
                .withArguments(
                        "--init-script",
                        initGradleCopy.getAbsolutePath(),
                        "--init-script",
                        probeGradleCopy.getAbsolutePath(),
                        "help",
                        "--quiet")
                .forwardOutput()
                .build();

        assertThat(buildResult.getOutput()).contains("DRM[0]: artifactory-mvn-plugins");
    }
}
