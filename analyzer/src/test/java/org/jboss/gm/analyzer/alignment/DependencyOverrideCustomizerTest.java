package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import org.aeonbits.owner.ConfigFactory;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.gm.analyzer.alignment.AlignmentService.Response;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.versioning.ProjectVersionFactory.withGAV;

public class DependencyOverrideCustomizerTest {

    private static final ProjectVersionRef PROJECT = withGAV("org.acme", "test", "1.0.0-redhat-00001");

    private static final String DEFAULT_SUFFIX = "-redhat-00001";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    private Project project;

    @Before
    public final void before() throws IOException {
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        project = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        project.setVersion(PROJECT.getVersionString());
        project.setGroup(PROJECT.getGroupId());
    }

    @Test
    public void ensureOverrideOfDependenciesWorks() {
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");
        // this gav will not be part of the original response
        final ProjectVersionRef jacksonGav = withGAV("com.fasterxml.jackson.core", "undertow-core", "2.9.9.3");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0");

        final String newProjectVersion = "1.0.0" + DEFAULT_SUFFIX;

        System.setProperty(DependencyOverrideCustomizer.DEPENDENCY_OVERRIDE + "org.hibernate:*@*", "5.3.10.Final-redhat-00001");
        System.setProperty(DependencyOverrideCustomizer.DEPENDENCY_OVERRIDE + "org.mockito:mockito-*@*", "");

        final Configuration configuration = ConfigFactory.create(Configuration.class);

        // make a customizer that overrides any hibernate dependency but no other gavs
        final DependencyOverrideCustomizer sut = new DependencyOverrideCustomizer(configuration,
                Collections.singleton(project));

        Response originalResp = new Response(
                new HashMap<ProjectVersionRef, String>() {
                    {
                        put(hibernateGav, hibernateGav.getVersionString() + DEFAULT_SUFFIX);
                        put(undertowGav, undertowGav.getVersionString() + DEFAULT_SUFFIX);
                        put(mockitoGav, mockitoGav.getVersionString() + DEFAULT_SUFFIX);
                    }
                });
        originalResp.setNewProjectVersion(newProjectVersion);

        // here we simply ensure that our original response has been properly setup
        assertThat(originalResp).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo(newProjectVersion);
            assertThat(r.getAlignedVersionOfGav(project, hibernateGav)).isEqualTo("5.3.7.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(project, undertowGav)).isEqualTo("2.0.15.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(project, mockitoGav)).isEqualTo("2.27.0-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(project, jacksonGav)).isEqualTo(null);
        });

        sut.customize(originalResp);
        assertThat(originalResp).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo(newProjectVersion);
            // make sure the matched dependency's version was changed by the dependency override customizer
            assertThat(r.getAlignedVersionOfGav(project, hibernateGav)).isEqualTo("5.3.10.Final-redhat-00001");

            // make sure that a gav aligned by the original response still yields with the original aligned version
            assertThat(r.getAlignedVersionOfGav(project, undertowGav)).isEqualTo("2.0.15.Final-redhat-00001");

            // make sure that a gav that was NOT aligned by the original response
            // does not return an aligned version after the dependency override customization is applied
            assertThat(r.getAlignedVersionOfGav(project, jacksonGav)).isEqualTo(null);

            assertThat(r.getAlignedVersionOfGav(project, mockitoGav)).isEqualTo("");
        });
    }

}
