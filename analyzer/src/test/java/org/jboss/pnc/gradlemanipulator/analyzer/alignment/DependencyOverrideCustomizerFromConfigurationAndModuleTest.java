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
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
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

    /** A second project that has no group set (mimics Ehcache utility/test modules). */
    private Project ungroupedProject;

    @Before
    public final void before() throws IOException {
        final File simpleProjectRoot = tempDir.newFolder("test");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion(PROJECT.getVersionString());
        p.setGroup(PROJECT.getGroupId());

        final File ungroupedProjectRoot = tempDir.newFolder("ungrouped-test");
        ungroupedProject = ProjectBuilder.builder().withProjectDir(ungroupedProjectRoot).build();
        ungroupedProject.setVersion("2.0.0-redhat-00001");
        // group deliberately left empty to simulate Ehcache-style ungrouped modules

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

    /**
     * Primary regression test for the Ehcache defect: a global empty override ({@code dependencyOverride.*:*@*=})
     * must suppress DA dependency alignments for both grouped and ungrouped Gradle projects.
     */
    @Test
    public void globalEmptyOverrideAppliesToGroupedAndUngroupedModules() throws ManipulationException {
        final ProjectVersionRef junitGav = withGAV("junit", "junit", "4.12.0.redhat-00001");
        final ProjectVersionRef slf4jGav = withGAV("org.slf4j", "slf4j-api", "1.7.32.redhat-00001");
        final ProjectVersionRef hamcrestGav = withGAV("org.hamcrest", "hamcrest-core", "1.3.0.redhat-00001");

        System.setProperty("dependencyOverride.*:*@*", "");

        final Configuration configuration = ConfigFactory.create(Configuration.class);

        // Both the grouped project (from @Before) and the ungrouped project are tested.
        final Set<Project> allProjects = new HashSet<>(projects);
        allProjects.add(ungroupedProject);

        final AlignmentService.Manipulator sut = new DependencyOverrideCustomizer(configuration, allProjects);
        final Map<ProjectVersionRef, String> translationMap = new HashMap<>();
        translationMap.put(junitGav, junitGav.getVersionString());
        translationMap.put(slf4jGav, slf4jGav.getVersionString());
        translationMap.put(hamcrestGav, hamcrestGav.getVersionString());

        final AlignmentService.Response response = new AlignmentService.Response(translationMap);
        final Project groupedProject = projects.stream().findFirst().get();
        response.getProjectOverrides().put(groupedProject, PROJECT.getVersionString());
        response.getProjectOverrides().put(ungroupedProject, "2.0.0-redhat-00001");

        sut.customize(response);

        // For the grouped project: DA translations must be suppressed by the empty override.
        assertThat(response.getAlignedVersionOfGav(groupedProject, junitGav)).isEqualTo("");
        assertThat(response.getAlignedVersionOfGav(groupedProject, slf4jGav)).isEqualTo("");
        assertThat(response.getAlignedVersionOfGav(groupedProject, hamcrestGav)).isEqualTo("");
        // Project-version override must remain unaffected.
        assertThat(response.getProjectOverrides().get(groupedProject)).isEqualTo(PROJECT.getVersionString());

        // For the ungrouped project: the @* rule must also suppress DA translations.
        assertThat(response.getAlignedVersionOfGav(ungroupedProject, junitGav)).isEqualTo("");
        assertThat(response.getAlignedVersionOfGav(ungroupedProject, slf4jGav)).isEqualTo("");
        assertThat(response.getAlignedVersionOfGav(ungroupedProject, hamcrestGav)).isEqualTo("");
        // Project-version override must remain unaffected.
        assertThat(response.getProjectOverrides().get(ungroupedProject)).isEqualTo("2.0.0-redhat-00001");
    }

    /**
     * A scoped module selector (not {@code @*}) must still apply only to projects whose
     * group + name matches, and must not accidentally apply to an ungrouped project.
     */
    @Test
    public void scopedSelectorDoesNotApplyToUngroupedModule() throws ManipulationException {
        final ProjectVersionRef junitGav = withGAV("junit", "junit", "4.12.0.redhat-00001");

        // Override applies only to org.acme:test, not to the ungrouped project.
        System.setProperty("dependencyOverride.junit:*@org.acme:test", "");

        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final Set<Project> allProjects = new HashSet<>(projects);
        allProjects.add(ungroupedProject);

        final AlignmentService.Manipulator sut = new DependencyOverrideCustomizer(configuration, allProjects);
        final Map<ProjectVersionRef, String> translationMap = new HashMap<>();
        translationMap.put(junitGav, junitGav.getVersionString());

        final AlignmentService.Response response = new AlignmentService.Response(translationMap);
        sut.customize(response);

        final Project groupedProject = projects.stream().findFirst().get();
        // Scoped rule must apply to the matching grouped project.
        assertThat(response.getAlignedVersionOfGav(groupedProject, junitGav)).isEqualTo("");
        // Ungrouped project must see the original DA translation (no override installed).
        assertThat(response.getAlignedVersionOfGav(ungroupedProject, junitGav))
                .isEqualTo(junitGav.getVersionString());
    }

    /**
     * A global rule must be installed in every project's override map regardless of
     * property iteration order, so that it can suppress DA translations via
     * {@code Response.getAlignedVersionOfGav()}.
     * <p>
     * When a global empty rule ({@code junit:*@*=}) and a specific nonempty rule
     * ({@code junit:junit@org.acme:test=4.12.0.redhat-00002}) both target the same
     * dependency, the global empty rule is installed via the wildcard key {@code junit:*}
     * and wins because {@code Response.matchingProjectRef()} returns the first key
     * that {@code ProjectRef.matches()} accepts — the wildcard key matches first.
     */
    @Test
    public void globalRuleOverridesSpecificRuleRegardlessOfDeclarationOrder() throws ManipulationException {
        final ProjectVersionRef junitGav = withGAV("junit", "junit", "4.12.0.redhat-00001");

        // Declare the specific rule first; the global rule should still dominate.
        System.setProperty("dependencyOverride.junit:junit@org.acme:test", "4.12.0.redhat-00002");
        System.setProperty("dependencyOverride.junit:*@*", "");

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final AlignmentService.Manipulator sut = new DependencyOverrideCustomizer(
                configuration,
                projects);

        final Map<ProjectVersionRef, String> translationMap = new HashMap<>();
        translationMap.put(junitGav, junitGav.getVersionString());
        final AlignmentService.Response response = new AlignmentService.Response(translationMap);
        sut.customize(response);

        final Project groupedProject = projects.stream().findFirst().get();
        // Both rules should be installed in the override map.
        // The global wildcard key (junit:*) matches junitGav, so getAlignedVersionOfGav returns "".
        assertThat(response.getAlignedVersionOfGav(groupedProject, junitGav)).isEqualTo("");
    }
}
