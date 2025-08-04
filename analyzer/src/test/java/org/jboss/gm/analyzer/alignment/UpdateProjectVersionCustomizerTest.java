package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
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

public class UpdateProjectVersionCustomizerTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void testVersionModificationDisabled() throws IOException, ManipulationException {
        System.setProperty("versionModification", "false");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        final String version = "1.0.0";
        p.setVersion(version);
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(configuration.versionModificationEnabled()).isFalse();
        assertThat(originalResp.getProjectOverrides().get(p)).isEqualTo(p.getVersion());
    }

    @Test
    public void testVersionOverride() throws IOException, ManipulationException {
        System.setProperty("versionOverride", "1.1.0.redhat-00002");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull();
        assertThat(originalResp.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00002");
        assertThat(configuration.versionOverride()).isEqualTo("1.1.0.redhat-00002");
    }

    @Test
    public void testVersionIncrementalSuffix() throws IOException, ManipulationException {
        System.setProperty("versionIncrementalSuffix", "foobar");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull();
        assertThat(originalResp.getProjectOverrides().get(p)).isEqualTo("1.1.0.foobar-00001");
        assertThat(configuration.versionIncrementalSuffix()).isEqualTo("foobar");
    }

    @Test
    public void testVersionSuffix() throws IOException, ManipulationException {
        System.setProperty("versionSuffix", "redhat-00002");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull();
        assertThat(configuration.versionSuffix()).isEqualTo("redhat-00002");
        assertThat(originalResp.getProjectOverrides().get(p)).isEqualTo("1.0.0.redhat-00002");
        assertThat(originalResp.getProjectOverrides().get(p)).isEqualTo("1.0.0.redhat-00002");
    }

    @Test
    public void testVersionOsgi() throws IOException, ManipulationException {
        System.setProperty("versionOsgi", "false");
        System.setProperty("versionSuffix", "Beta1");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull();
        assertThat(originalResp.getProjectOverrides().get(p)).isEqualTo("1.Beta1");
        assertThat(configuration.versionOsgi()).isFalse();
        assertThat(configuration.versionSuffix()).isEqualTo("Beta1");
    }

    @Test
    public void versionSuffixAlternatives() throws IOException, ManipulationException {
        System.setProperty("versionSuffixAlternatives", "foobar,redhat");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull();
        assertThat(originalResp.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00001");
        assertThat(configuration.versionSuffixAlternatives()).isEqualTo("foobar,redhat");
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion()
            throws IOException, ManipulationException {
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.0.0");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.0.0.redhat-00001"));
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion2()
            throws IOException, ManipulationException {
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00001"));
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasNoProjectVersion3()
            throws IOException, ManipulationException {

        System.setProperty("versionIncrementalSuffixPadding", "3");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-001"));
    }

    @Test
    public void ensureProjectVersionIsUpdatedWhenOriginalResponseHasProperProjectVersion()
            throws IOException, ManipulationException {

        System.setProperty("ignoreUnresolvableDependencies", "true");

        final ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org:dummy:1.1.0.redhat-00001");

        final Response originalResp = new Response(Collections.singletonMap(pvr, pvr.getVersionString()));
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion(pvr.getVersionString());
        p.setGroup("org");
        final ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(null, pvr);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00002"));
    }

    @Test
    public void validateVersionWithSnapshot() throws IOException, ManipulationException {

        System.setProperty("versionSuffixSnapshot", "true");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00001-SNAPSHOT"));
        assertThat(configuration.versionSuffixSnapshot()).isTrue();
    }

    @Test
    public void validateVersionWithNoSnapshot() throws IOException, ManipulationException {

        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response originalResp = new Response(Collections.emptyMap());
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00001"));
        assertThat(configuration.versionSuffixSnapshot()).isFalse();
    }

    @Test
    public void validateVersionWithSnapshotIncrementalKept() throws IOException, ManipulationException {

        System.setProperty("versionSuffixSnapshot", "true");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final ProjectVersionRef rg = SimpleProjectVersionRef.parse("org:test:1.1-SNAPSHOT");
        final Map<ProjectVersionRef, String> translation = Collections.singletonMap(rg, "1.1.0.redhat-00001");
        final Response originalResp = new Response(translation);
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(p, rg);

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00002-SNAPSHOT"));
    }

    @Test
    public void validateVersionWithSnapshotIncrement() throws IOException, ManipulationException {

        System.setProperty("ignoreUnresolvableDependencies", "true");

        final ProjectVersionRef rg = SimpleProjectVersionRef.parse("org:test:1.1");
        final Map<ProjectVersionRef, String> translation = Collections.singletonMap(rg, "1.1.0.redhat-00001");
        final Response originalResp = new Response(translation);
        final File simpleProjectRoot = tempDir.newFolder("simple-project");
        final Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();
        p.setVersion("1.1-SNAPSHOT");
        p.setGroup("org");
        final ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(p, new SimpleProjectVersionRef(p.getGroup().toString(), p.getName(), p.getVersion().toString()));

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final UpdateProjectVersionCustomizer sut = new UpdateProjectVersionCustomizer(configuration, p);
        sut.customize(originalResp);

        assertThat(originalResp).isNotNull()
                .satisfies(r -> assertThat(r.getProjectOverrides().get(p)).isEqualTo("1.1.0.redhat-00002"));
    }
}
