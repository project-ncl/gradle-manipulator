package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aeonbits.owner.ConfigFactory;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.versioning.ProjectVersionFactory.withGAV;

public class DependencyOverrideCustomizerFromConfigurationAndModuleTest {

    private static final ProjectVersionRef PROJECT = withGAV("org.acme", "test", "1.0.0-redhat-00001");

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Before
    public final void before() throws IOException {
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion(PROJECT.getVersionString());
        p.setGroup(PROJECT.getGroupId());

        projects = new HashSet<>();
        projects.add(p);
    }

    private Set<Project> projects;

    @Test
    public void noDependencyOverrideProperty() {
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final AlignmentService.ResponseCustomizer sut = DependencyOverrideCustomizer.fromConfigurationForModule(configuration,
                projects);

        assertThat(sut).isSameAs(null);
    }

    @Test(expected = InvalidUserDataException.class)
    public void erroneousPropertiesCauseFailure() {
        System.setProperty("dependencyOverride.org.acme", "");
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        DependencyOverrideCustomizer.fromConfigurationForModule(configuration, projects);
    }

    @Test
    public void ensureOverrideMatches() throws ManipulationException {
        final ProjectVersionRef hibernateCoreGav = withGAV("org.hibernate", "hibernate-core",
                "5.3.9.Final-redhat-00001");
        final ProjectVersionRef hibernateValidatorGav = withGAV("org.hibernate", "hibernate-validator",
                "6.0.16.Final-redhat-00001");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core",
                "2.0.15.Final-redhat-00001");
        final ProjectVersionRef jacksonCoreGav = withGAV("com.fasterxml.jackson.core", "jackson-core",
                "2.9.8-redhat-00001");
        final ProjectVersionRef jacksonMapperGav = withGAV("com.fasterxml.jackson.core", "jackson-mapper",
                "2.9.8-redhat-00001");
        final ProjectVersionRef mongoGav = withGAV("org.mongodb", "mongo-java-driver", "3.10.2-redhat-00001");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0-redhat-00001");
        final ProjectVersionRef wiremockGav = withGAV("com.github.tomakehurst", "wiremock-jre8",
                "2.23.2-redhat-00001");

        System.setProperty("dependencyOverride.org.hibernate:hibernate-core@*",
                "5.3.7.Final-redhat-00001"); // should result in overriding only hibernate-core dependency
        System.setProperty("dependencyOverride.com.fasterxml.jackson.core:*@*",
                "2.9.5-redhat-00001"); // should result in overriding all jackson dependencies
        System.setProperty("dependencyOverride.io.undertow:undertow-servlet@*",
                "2.0.14.Final-redhat-00001"); // should NOT result in overriding the undertow dependency since the artifact doesn't match
        System.setProperty("dependencyOverride.org.mockito:*@org.acme:test",
                "2.27.0-redhat-00002"); // should result in overriding the mockito dependency
        System.setProperty("dependencyOverride.com.github.tomakehurst:*@org.acme:other",
                ""); // should NOT result overriding the wiremock dependency since the module doesn't match

        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final AlignmentService.ResponseCustomizer sut = DependencyOverrideCustomizer.fromConfigurationForModule(configuration,
                projects);
        final Map<ProjectVersionRef, String> translationMap = new HashMap<>();
        final List<ProjectVersionRef> gavs = Arrays.asList(hibernateCoreGav, hibernateValidatorGav, undertowGav, jacksonCoreGav,
                jacksonMapperGav,
                mongoGav, mockitoGav, wiremockGav);

        gavs.forEach(d -> translationMap.put(d, d.getVersionString()));

        final AlignmentService.Response originalResp = new AlignmentService.Response(Collections.singletonList(PROJECT),
                translationMap);
        originalResp.setNewProjectVersion(PROJECT.getVersionString());

        final AlignmentService.Response finalResp = sut.customize(originalResp);

        assertThat(finalResp).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo(PROJECT.getVersionString());
            assertThat(r.getAlignedVersionOfGav(hibernateCoreGav)).isEqualTo("5.3.7.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(hibernateValidatorGav)).isEqualTo(hibernateValidatorGav.getVersionString());
            assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo(undertowGav.getVersionString());
            assertThat(r.getAlignedVersionOfGav(jacksonCoreGav)).isEqualTo("2.9.5-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(mockitoGav)).isEqualTo("2.27.0-redhat-00002");
            assertThat(r.getAlignedVersionOfGav(wiremockGav)).isEqualTo(wiremockGav.getVersionString());
        });
    }
}
