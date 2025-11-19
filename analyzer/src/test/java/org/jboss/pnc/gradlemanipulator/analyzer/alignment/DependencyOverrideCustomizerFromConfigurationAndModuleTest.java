package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.gradlemanipulator.common.versioning.ProjectVersionFactory.withGAV;

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
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

public class DependencyOverrideCustomizerFromConfigurationAndModuleTest {

    private static final ProjectVersionRef PROJECT = withGAV("org.acme", "test", "1.0.0-redhat-00001");

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    private Set<Project> projects;

    @Before
    public final void before() throws IOException {
        final File simpleProjectRoot = tempDir.newFolder("test");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion(PROJECT.getVersionString());
        p.setGroup(PROJECT.getGroupId());

        projects = new HashSet<>();
        projects.add(p);
    }

    @Test
    public void noDependencyOverrideProperty() {
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        AlignmentService.Response response = new AlignmentService.Response(Collections.emptyMap());
        DependencyOverrideCustomizer dc = new DependencyOverrideCustomizer(configuration, projects);
        dc.customize(response);

        assertThat(response.getDependencyOverrides()).isEmpty();
    }

    @Test(expected = InvalidUserDataException.class)
    public void erroneousPropertiesCauseFailure() {
        System.setProperty("dependencyOverride.org.acme", "");
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        new DependencyOverrideCustomizer(configuration, projects)
                .customize(new AlignmentService.Response(Collections.emptyMap()));
    }

    @Test
    public void ensureOverrideMatches() throws ManipulationException {
        final ProjectVersionRef hibernateCoreGav = withGAV(
                "org.hibernate",
                "hibernate-core",
                "5.3.9.Final-redhat-00001");
        final ProjectVersionRef hibernateValidatorGav = withGAV(
                "org.hibernate",
                "hibernate-validator",
                "6.0.16.Final-redhat-00001");
        final ProjectVersionRef undertowGav = withGAV(
                "io.undertow",
                "undertow-core",
                "2.0.15.Final-redhat-00001");
        final ProjectVersionRef jacksonCoreGav = withGAV(
                "com.fasterxml.jackson.core",
                "jackson-core",
                "2.9.8-redhat-00001");
        final ProjectVersionRef jacksonMapperGav = withGAV(
                "com.fasterxml.jackson.core",
                "jackson-mapper",
                "2.9.8-redhat-00001");
        final ProjectVersionRef mongoGav = withGAV("org.mongodb", "mongo-java-driver", "3.10.2-redhat-00001");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0-redhat-00001");
        final ProjectVersionRef wiremockGav = withGAV(
                "com.github.tomakehurst",
                "wiremock-jre8",
                "2.23.2-redhat-00001");

        System.setProperty(
                "dependencyOverride.org.hibernate:hibernate-core@*",
                "5.3.7.Final-redhat-00001"); // should result in overriding only hibernate-core dependency
        System.setProperty(
                "dependencyOverride.com.fasterxml.jackson.core:*@*",
                "2.9.5-redhat-00001"); // should result in overriding all jackson dependencies
        System.setProperty(
                "dependencyOverride.io.undertow:undertow-servlet@*",
                "2.0.14.Final-redhat-00001"); // should NOT result in overriding the undertow dependency since the artifact doesn't match
        System.setProperty(
                "dependencyOverride.org.mockito:*@org.acme:test",
                "2.27.0-redhat-00002"); // should result in overriding the mockito dependency
        System.setProperty(
                "dependencyOverride.com.github.tomakehurst:*@org.acme:other",
                ""); // should NOT result overriding the wiremock dependency since the module doesn't match

        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final AlignmentService.Manipulator sut = new DependencyOverrideCustomizer(configuration, projects);
        final Map<ProjectVersionRef, String> translationMap = new HashMap<>();
        final List<ProjectVersionRef> gavs = Arrays.asList(
                hibernateCoreGav,
                hibernateValidatorGav,
                undertowGav,
                jacksonCoreGav,
                jacksonMapperGav,
                mongoGav,
                mockitoGav,
                wiremockGav);

        gavs.forEach(d -> translationMap.put(d, d.getVersionString()));

        final AlignmentService.Response originalResp = new AlignmentService.Response(
                translationMap);
        projects.forEach(p -> originalResp.getProjectOverrides().put(p, PROJECT.getVersionString()));

        sut.customize(originalResp);

        final Project project = projects.stream().findFirst().get();

        assertThat(originalResp).isNotNull().satisfies(r -> {
            assertThat(r.getProjectOverrides().get(project)).isEqualTo(PROJECT.getVersionString());
            assertThat(r.getAlignedVersionOfGav(project, hibernateCoreGav)).isEqualTo("5.3.7.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(project, hibernateValidatorGav))
                    .isEqualTo(hibernateValidatorGav.getVersionString());
            assertThat(r.getAlignedVersionOfGav(project, undertowGav)).isEqualTo(undertowGav.getVersionString());
            assertThat(r.getAlignedVersionOfGav(project, jacksonCoreGav)).isEqualTo("2.9.5-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(project, mockitoGav)).isEqualTo("2.27.0-redhat-00002");
            assertThat(r.getAlignedVersionOfGav(project, wiremockGav)).isEqualTo(wiremockGav.getVersionString());
        });
    }
}
