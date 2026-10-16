package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.pnc.gradlemanipulator.common.utils.ProjectUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests {@link ProjectUtils#updateNameField(Project, Object)} across the Gradle version range.
 * <p>
 * In Gradle &lt; 9.8 the {@code name} field lives directly on {@code DefaultProject}.
 * In Gradle &gt;= 9.8 it was moved to {@code ProjectIdentity.projectName} (PR #34868).
 * {@code updateNameField} must handle both layouts via a version-gated reflection path.
 * <p>
 * This test runs under whichever Gradle version drives the build (i.e. every version in the CI
 * matrix), so both code paths are exercised automatically as the matrix is extended.
 */
public class ProjectUtilsUpdateNameFieldTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void updateNameFieldChangesProjectName() throws IOException {
        final File projectDir = tempDir.newFolder("my-project");
        final Project project = ProjectBuilder.builder()
                .withName("my-project")
                .withProjectDir(projectDir)
                .build();

        assertEquals("my-project", project.getName());

        ProjectUtils.updateNameField(project, "renamed-artifact");

        assertEquals("renamed-artifact", project.getName());
    }

    @Test
    public void updateNameFieldToSameValueIsIdempotent() throws IOException {
        final File projectDir = tempDir.newFolder("stable");
        final Project project = ProjectBuilder.builder()
                .withName("stable")
                .withProjectDir(projectDir)
                .build();

        ProjectUtils.updateNameField(project, "stable");

        assertEquals("stable", project.getName());
    }

    @Test
    public void updateNameFieldAcceptsNumericName() throws IOException {
        final File projectDir = tempDir.newFolder("proj");
        final Project project = ProjectBuilder.builder()
                .withName("proj")
                .withProjectDir(projectDir)
                .build();

        ProjectUtils.updateNameField(project, "123-artifact");

        assertEquals("123-artifact", project.getName());
    }
}
