package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aeonbits.owner.ConfigFactory;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.gm.analyzer.alignment.AlignmentService.Response;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

public class UpdateProjectVersionCustomizerTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion()
            throws IOException, ManipulationException {
        final Response originalResp = new Response(new HashMap<>());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.0.0");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.0.0.redhat-00001"));
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion2()
            throws IOException, ManipulationException {
        final Response originalResp = new Response(new HashMap<>());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00001"));
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion3()
            throws IOException, ManipulationException {

        System.setProperty("versionIncrementalSuffixPadding", "3");

        final Response originalResp = new Response(new HashMap<>());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-001"));
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasProperProjectVersion()
            throws IOException, ManipulationException {

        final ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org:dummy:1.1.0.redhat-00001");

        final Response originalResp = new Response(
                new HashMap<ProjectVersionRef, String>() {
                    {
                        put(pvr, pvr.getVersionString());
                    }
                });
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion(pvr.getVersionString());
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(null, pvr);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00002"));
    }

    @Test
    public void validateVersionWithSnapshot() throws IOException, ManipulationException {

        System.setProperty("versionSuffixSnapshot", "true");

        final Response originalResp = new Response(new HashMap<>());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00001-SNAPSHOT"));
        assertTrue(configuration.versionSuffixSnapshot());
    }

    @Test
    public void validateVersionWithNoSnapshot() throws IOException, ManipulationException {

        final Response originalResp = new Response(new HashMap<>());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00001"));
        assertFalse(configuration.versionSuffixSnapshot());
    }

    @Test
    public void validateVersionWithSnapshotIncrementalKept() throws IOException, ManipulationException {

        System.setProperty("versionSuffixSnapshot", "true");

        Map<ProjectVersionRef, String> translation = new HashMap<>();
        ProjectVersionRef rg = SimpleProjectVersionRef.parse("org:test:1.1-SNAPSHOT");
        translation.put(rg, "1.1.0.redhat-00001");

        final Response originalResp = new Response(translation);
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(p, rg);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00002-SNAPSHOT"));
    }

    @Test
    public void validateVersionWithSnapshotIncrement() throws IOException, ManipulationException {

        Map<ProjectVersionRef, String> translation = new HashMap<>();
        ProjectVersionRef rg = SimpleProjectVersionRef.parse("org:test:1.1");
        translation.put(rg, "1.1.0.redhat-00001");

        final Response originalResp = new Response(translation);
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        System.setProperty("ignoreUnresolvableDependencies", "true");
        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Set<Project> projects = new HashSet<>();
        projects.add(p);

        ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(p, new SimpleProjectVersionRef(p.getGroup().toString(), p.getName(), p.getVersion().toString()));

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(projects, configuration);
        final Response customizedReq = sut.customize(originalResp);

        assertThat(customizedReq).isNotNull()
                .satisfies(r -> assertThat(r.getNewProjectVersion()).isEqualTo("1.1.0.redhat-00002"));
    }
}
