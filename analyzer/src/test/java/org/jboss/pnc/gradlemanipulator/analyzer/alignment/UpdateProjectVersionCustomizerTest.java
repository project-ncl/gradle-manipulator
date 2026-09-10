package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.aeonbits.owner.ConfigFactory;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.AlignmentService.Response;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.ManipulationCache;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

// Tests for enforceVersionPrefix (Step 4 of the enforce-version-prefix plan)
// All scenarios use ignoreUnresolvableDependencies=true unless a translation map is provided.

public class UpdateProjectVersionCustomizerTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

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

    // -------------------------------------------------------------------------
    // enforceVersionPrefix tests
    // -------------------------------------------------------------------------

    /** Scenario 1: property absent — existing behaviour unchanged. */
    @Test
    public void enforceVersionPrefix_absent_defaultBehaviour() throws IOException, ManipulationException {
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response resp = new Response(Collections.emptyMap());
        final File dir = tempDir.newFolder("evp-absent");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.2.0.Final");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        // PME uses a dash delimiter before the suffix when the base version has a qualifier.
        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("1.2.0.Final-redhat-00001");
    }

    /** Scenario 2: prefix missing, qualified version — dash delimiter expected. */
    @Test
    public void enforceVersionPrefix_qualifiedVersionMissingPrefix() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response resp = new Response(Collections.emptyMap());
        final File dir = tempDir.newFolder("evp-qualified");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.2.0.Final");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("1.2.0.Final-rhlw-00000-n-00001");
    }

    /** Scenario 3: prefix missing, numeric-only version — dot delimiter expected. */
    @Test
    public void enforceVersionPrefix_numericVersionMissingPrefix() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response resp = new Response(Collections.emptyMap());
        final File dir = tempDir.newFolder("evp-numeric");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.2.0");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("1.2.0.rhlw-00000-n-00001");
    }

    /** Scenario 4: prefix already present (5-digit form) — no duplication. */
    @Test
    public void enforceVersionPrefix_alreadyPresent() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response resp = new Response(Collections.emptyMap());
        final File dir = tempDir.newFolder("evp-present");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.2.0.Final-rhlw-00003");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("1.2.0.Final-rhlw-00003-n-00001");
    }

    /** Scenario 5: version already has both prefix and incremental suffix — increments suffix only. */
    @Test
    public void enforceVersionPrefix_alreadyComplete() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org:test:1.2.5.rhlw-00000-n-00001");
        final Response resp = new Response(Collections.singletonMap(pvr, pvr.getVersionString()));
        final File dir = tempDir.newFolder("evp-complete");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.2.5.rhlw-00000-n-00001");
        p.setGroup("org");
        final ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(null, pvr);
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("1.2.5.rhlw-00000-n-00002");
    }

    /**
     * Scenario 6: versionOverride sets the base; the incremental suffix is still appended on top.
     * enforceVersionPrefix is irrelevant once versionOverride is set.
     */
    @Test
    public void enforceVersionPrefix_overrideWins() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionOverride", "9.9.9.override");
        System.setProperty("versionIncrementalSuffix", "redhat");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response resp = new Response(Collections.emptyMap());
        final File dir = tempDir.newFolder("evp-override");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.2.0.Final");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("9.9.9.override-redhat-00001");
    }

    /** Scenario 7: property alone (no other suffix) activates versioning — enforced base returned. */
    @Test
    public void enforceVersionPrefix_aloneActivatesVersioning() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        // Suppress the default versionIncrementalSuffix so only prefix enforcement runs.
        System.setProperty("versionIncrementalSuffix", "");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Response resp = new Response(Collections.emptyMap());
        final File dir = tempDir.newFolder("evp-alone");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.0.0");
        p.setGroup("org");
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        // With empty versionIncrementalSuffix, PME uses rhlw as the sole suffix token and
        // produces the first build counter: 1.0.0.rhlw-00001.
        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("1.0.0.rhlw-00001");
    }

    /**
     * Scenario 8: REST candidate found via normalised project version — increment applied.
     *
     * <p>
     * The cache PVR is registered as the normalised form (the version that {@link AlignmentTask}
     * stores after prefix enforcement). The translation map maps that normalised PVR to the existing
     * candidate version, so the calculator should produce the next increment.
     */
    @Test
    public void enforceVersionPrefix_restCandidateIncrements() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final File dir = tempDir.newFolder("evp-rest");
        final Project p = ProjectBuilder.builder().withProjectDir(dir).build();
        p.setVersion("1.0.0.Final");
        p.setGroup("org");

        // The project name is the temp-folder name; use it in the PVRs so the cache lookup
        // in UpdateProjectVersionCustomizer (which filters by groupId + artifactId) finds a match.
        final String projectName = p.getName();
        final ProjectVersionRef normalisedPvr = new SimpleProjectVersionRef(
                "org",
                projectName,
                "1.0.0.Final-rhlw-00000");
        // Translation map: normalised base PVR → already-aligned candidate version string.
        final Response resp = new Response(
                Collections.singletonMap(normalisedPvr, "1.0.0.Final-rhlw-00000-n-00001"));
        final ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(null, normalisedPvr);
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, p).customize(resp);

        assertThat(resp.getProjectOverrides().get(p)).isEqualTo("1.0.0.Final-rhlw-00000-n-00002");
    }
}
