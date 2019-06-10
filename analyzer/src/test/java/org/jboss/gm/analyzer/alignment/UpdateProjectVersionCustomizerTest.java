package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aeonbits.owner.ConfigFactory;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.mockito.stubbing.Answer;

public class UpdateProjectVersionCustomizerTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion() throws IOException {
        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getNewProjectVersion()).thenReturn(null);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.0.0");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.0.0.redhat-00001");
        });
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion2() throws IOException {
        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getNewProjectVersion()).thenReturn(null);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00001");
        });
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion3() throws IOException {

        System.setProperty("versionIncrementalSuffixPadding", "3");

        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getNewProjectVersion()).thenReturn(null);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-001");
        });
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasProperProjectVersion() throws IOException {

        final ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org:dummy:1.1.0.redhat-00001");

        final AlignmentService.Response originalResp = mock(AlignmentService.Response.class);
        // just return whatever was passed
        when(originalResp.getAlignedVersionOfGav(any(ProjectVersionRef.class))).thenAnswer((Answer<String>) invocation -> {
            final ProjectVersionRef input = (ProjectVersionRef) invocation.getArguments()[0];
            return input.getVersionString();
        });
        when(originalResp.getTranslationMap()).thenAnswer((Answer<Map<ProjectVersionRef, String>>) invocation -> {
            Map<ProjectVersionRef, String> result = new HashMap<>();
            result.put(pvr, pvr.getVersionString());
            return result;
        });

        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion(pvr.getVersionString());
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        when(originalResp.getNewProjectVersion()).thenReturn(pvr.getVersionString());

        ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(pvr);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final AlignmentService.Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00002");
        });
    }
}
