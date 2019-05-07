package org.jboss.gm.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SimpleProjectWithMisconfiguredSpringDMAndMavenPluginsFunctionalTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void ensureProperPomGenerated() throws IOException, URISyntaxException {
        // this makes gradle use the set property as maven local directory
        // we do this in order to avoid polluting the maven local and also be absolutely sure
        // that no prior invocations affect the execution
        final File m2Directory = tempDir.newFolder(".m2");
        System.setProperty("maven.repo.local", m2Directory.getAbsolutePath());

        final File simpleProjectRoot = tempDir.newFolder("simple-project-with-misconfigured-spring-dm-and-maven-plugins");
        TestUtils.copyDirectory("simple-project-with-misconfigured-spring-dm-and-maven-plugins", simpleProjectRoot);
        assertThat(simpleProjectRoot.toPath().resolve("build.gradle")).exists();

        assertThatExceptionOfType(UnexpectedBuildFailure.class).isThrownBy(() -> {
            GradleRunner.create()
                    .withProjectDir(simpleProjectRoot)
                    .withArguments("install")
                    .withDebug(true)
                    .withPluginClasspath()
                    .build();
        }).withMessageContaining("cannot be used together");
    }
}
