package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    // -------------------------------------------------------------------------
    // NCLSUP-1773: lookup base version vs calculation base version (scenarios D1-D9)
    //
    // The cache PVR is registered the way AlignmentTask does it: published GA + the (reused,
    // prefix-normalised) base version that was sent to DA. The Gradle project name deliberately
    // differs from the published artifactId in D1/D2 (Gradle project 'core' published as
    // 'solr-core').
    // -------------------------------------------------------------------------

    private static final String SOLR_GROUP = "org";

    private Project newProject(String name) throws IOException {
        final File dir = tempDir.newFolder(name);
        final Project p = ProjectBuilder.builder().withName(name).withProjectDir(dir).build();
        p.setVersion("10.0.0");
        p.setGroup(SOLR_GROUP);
        return p;
    }

    private String calculate(Project root, Project target, Map<ProjectVersionRef, String> translation)
            throws ManipulationException {
        final Response resp = new Response(translation);
        final Configuration configuration = ConfigFactory.create(Configuration.class);
        new UpdateProjectVersionCustomizer(configuration, root).customize(resp);
        return resp.getProjectOverrides().get(target);
    }

    private void writeManipulationJson(File rootDir, String rootVersion, String childName, String childVersion)
            throws IOException {
        final String json = "{\n" +
                "  \"group\" : \"" + SOLR_GROUP + "\",\n" +
                "  \"name\" : \"" + rootDir.getName() + "\",\n" +
                "  \"projectPathName\" : \"" + rootDir.getName() + "\",\n" +
                "  \"version\" : \"" + rootVersion + "\",\n" +
                "  \"originalVersion\" : \"10.0.0\",\n" +
                "  \"children\" : {\n" +
                "    \"" + childName + "\" : {\n" +
                "      \"group\" : \"" + SOLR_GROUP + "\",\n" +
                "      \"name\" : \"solr-" + childName + "\",\n" +
                "      \"projectPathName\" : \"" + childName + "\",\n" +
                "      \"version\" : \"" + childVersion + "\",\n" +
                "      \"originalVersion\" : \"10.0.0\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n";
        Files.write(new File(rootDir, "manipulation.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private static int counterOf(String version) {
        final Matcher m = Pattern.compile("-(\\d+)(-SNAPSHOT)?$").matcher(version);
        assertThat(m.find()).as("no trailing counter in '%s'", version).isTrue();
        return Integer.parseInt(m.group(1));
    }

    /** D1: Gradle name 'core' != published artifactId 'solr-core'; reused base must be kept. */
    @Test
    public void nclsup1773_D1_nameMismatch_reusedBaseKept() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("core");
        ManipulationCache.getCache(p)
                .addGAV(p, new SimpleProjectVersionRef(SOLR_GROUP, "solr-core", "10.0.0.rhlw-00003"));

        assertThat(calculate(p, p, Collections.emptyMap())).isEqualTo("10.0.0.rhlw-00003-n-00001");
    }

    /** D2: as D1 plus a DA translation for the reused base. */
    @Test
    public void nclsup1773_D2_nameMismatch_translationIncrements() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("core");
        final ProjectVersionRef pvr = new SimpleProjectVersionRef(SOLR_GROUP, "solr-core", "10.0.0.rhlw-00003");
        ManipulationCache.getCache(p).addGAV(p, pvr);

        assertThat(calculate(p, p, Collections.singletonMap(pvr, "10.0.0.rhlw-00003-n-00004")))
                .isEqualTo("10.0.0.rhlw-00003-n-00005");
    }

    /** D3 (control): as D1 with project name == artifactId. */
    @Test
    public void nclsup1773_D3_nameMatch_control() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("solr-core");
        ManipulationCache.getCache(p)
                .addGAV(p, new SimpleProjectVersionRef(SOLR_GROUP, "solr-core", "10.0.0.rhlw-00003"));

        assertThat(calculate(p, p, Collections.emptyMap())).isEqualTo("10.0.0.rhlw-00003-n-00001");
    }

    /** D4: a candidate from an older lineage (rhlw-00000-n-...) must not advance the rhlw-00003-n counter. */
    @Test
    public void nclsup1773_D4_lineageIsolation() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("solr-core");
        final ProjectVersionRef pvr = new SimpleProjectVersionRef(SOLR_GROUP, "solr-core", "10.0.0.rhlw-00003");
        ManipulationCache.getCache(p).addGAV(p, pvr);

        assertThat(calculate(p, p, Collections.singletonMap(pvr, "10.0.0.rhlw-00000-n-00016")))
                .isEqualTo("10.0.0.rhlw-00003-n-00001");
    }

    /** D5: pooling - translations of all cached PVRs feed one project; the max wins (locks section 3.4). */
    @Test
    public void nclsup1773_D5_pooledTranslationsUseMax() throws IOException, ManipulationException {
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("alpha");
        final ProjectVersionRef alpha = new SimpleProjectVersionRef(SOLR_GROUP, "alpha", "10.0.0");
        final ProjectVersionRef beta = new SimpleProjectVersionRef(SOLR_GROUP, "beta", "10.0.0");
        final ManipulationCache cache = ManipulationCache.getCache(p);
        cache.addGAV(p, alpha);
        cache.addGAV(null, beta);
        final Map<ProjectVersionRef, String> translation = new HashMap<>();
        translation.put(alpha, "10.0.0.redhat-00002");
        translation.put(beta, "10.0.0.redhat-00004");

        assertThat(calculate(p, p, translation)).isEqualTo("10.0.0.redhat-00005");
    }

    /** D6a: snapshot suffix preserved. */
    @Test
    public void nclsup1773_D6a_snapshotPreserved() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("versionSuffixSnapshot", "true");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("solr-core");
        p.setVersion("10.0.0.rhlw-00003-SNAPSHOT");
        final ProjectVersionRef pvr = new SimpleProjectVersionRef(
                SOLR_GROUP,
                "solr-core",
                "10.0.0.rhlw-00003-SNAPSHOT");
        ManipulationCache.getCache(p).addGAV(p, pvr);

        assertThat(calculate(p, p, Collections.singletonMap(pvr, "10.0.0.rhlw-00003-n-00001")))
                .isEqualTo("10.0.0.rhlw-00003-n-00002-SNAPSHOT");
    }

    /** D6b: snapshot suffix not preserved. */
    @Test
    public void nclsup1773_D6b_snapshotStripped() throws IOException, ManipulationException {
        System.setProperty("enforceVersionPrefix", "rhlw");
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("solr-core");
        p.setVersion("10.0.0.rhlw-00003-SNAPSHOT");
        ManipulationCache.getCache(p)
                .addGAV(p, new SimpleProjectVersionRef(SOLR_GROUP, "solr-core", "10.0.0.rhlw-00003-SNAPSHOT"));
        // With versionSuffixSnapshot=false the translation map key has the snapshot stripped.
        final ProjectVersionRef key = new SimpleProjectVersionRef(SOLR_GROUP, "solr-core", "10.0.0.rhlw-00003");

        assertThat(calculate(p, p, Collections.singletonMap(key, "10.0.0.rhlw-00003-n-00001")))
                .isEqualTo("10.0.0.rhlw-00003-n-00002");
    }

    /**
     * D7 (invariant, json candidates): manipulation.json has root rhlw-00002 and child 'core' rhlw-00005. The
     * calculation for 'core' (base rhlw-00005) must never reproduce an existing version, i.e. the counter must be
     * &gt; 5. Candidates currently only contain the root node version so this is expected to FAIL (duplicate).
     */
    @Test
    public void nclsup1773_D7_invariant_jsonChildNodeCounterIsRespected() throws IOException, ManipulationException {
        System.setProperty("versionIncrementalSuffix", "rhlw");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final File rootDir = tempDir.newFolder("solr-root");
        final Project root = ProjectBuilder.builder().withName("solr-root").withProjectDir(rootDir).build();
        root.setVersion("10.0.0");
        root.setGroup(SOLR_GROUP);
        final Project core = ProjectBuilder.builder().withName("core").withParent(root).build();
        core.setVersion("10.0.0");
        core.setGroup(SOLR_GROUP);
        writeManipulationJson(rootDir, "10.0.0.rhlw-00002", "core", "10.0.0.rhlw-00005");
        // Names match here on purpose: D7 isolates the candidate-set defect from the name-mismatch defect (D1/D2).
        ManipulationCache.getCache(root)
                .addGAV(core, new SimpleProjectVersionRef(SOLR_GROUP, "core", "10.0.0.rhlw-00005"));

        final String result = calculate(root, core, Collections.emptyMap());

        assertThat(result).isNotEqualTo("10.0.0.rhlw-00005");
        assertThat(counterOf(result)).as("counter of '%s' must exceed highest known counter 5", result)
                .isGreaterThan(5);
    }

    /** D8 (invariant): DA translation beats the json candidate (same lineage). */
    @Test
    public void nclsup1773_D8_invariant_daBeatsJson() throws IOException, ManipulationException {
        System.setProperty("versionIncrementalSuffix", "rhlw");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("core");
        writeManipulationJson(p.getRootDir(), "10.0.0.rhlw-00003", "core", "10.0.0.rhlw-00003");
        final ProjectVersionRef pvr = new SimpleProjectVersionRef(SOLR_GROUP, "core", "10.0.0.rhlw-00003");
        ManipulationCache.getCache(p).addGAV(p, pvr);

        assertThat(calculate(p, p, Collections.singletonMap(pvr, "10.0.0.rhlw-00007")))
                .isEqualTo("10.0.0.rhlw-00008");
    }

    /** D9 (invariant): DA translation beats the json candidate (new 'n' lineage). */
    @Test
    public void nclsup1773_D9_invariant_daBeatsJson_newLineage() throws IOException, ManipulationException {
        System.setProperty("versionIncrementalSuffix", "n");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project p = newProject("core");
        writeManipulationJson(p.getRootDir(), "10.0.0.rhlw-00003", "core", "10.0.0.rhlw-00003");
        final ProjectVersionRef pvr = new SimpleProjectVersionRef(SOLR_GROUP, "core", "10.0.0.rhlw-00003");
        ManipulationCache.getCache(p).addGAV(p, pvr);

        assertThat(calculate(p, p, Collections.singletonMap(pvr, "10.0.0.rhlw-00003-n-00007")))
                .isEqualTo("10.0.0.rhlw-00003-n-00008");
    }

    /** F1 (NCLSUP-967 guard): differing base lineages stay independent under pooled candidates. */
    @Test
    public void nclsup967_F1_differentBaseLineagesStayIndependent() throws IOException, ManipulationException {
        System.setProperty("versionIncrementalSuffix", "redhat");
        System.setProperty("ignoreUnresolvableDependencies", "true");

        final Project root = newProject("root");
        root.setVersion("0.17.0");
        final Project alpha = ProjectBuilder.builder().withName("bom-alpha").withParent(root).build();
        alpha.setVersion("0.17.0-alpha");

        final ProjectVersionRef rootPvr = new SimpleProjectVersionRef(
                "io.opentelemetry",
                "opentelemetry-java",
                "0.17.0");
        final ProjectVersionRef alphaPvr = new SimpleProjectVersionRef(
                "io.opentelemetry",
                "opentelemetry-bom-alpha",
                "0.17.0-alpha");

        final ManipulationCache cache = ManipulationCache.getCache(root);
        cache.addGAV(root, rootPvr);
        cache.addGAV(alpha, alphaPvr);

        final Map<ProjectVersionRef, String> translations = new HashMap<>();
        translations.put(rootPvr, "0.17.0.redhat-00004");
        translations.put(alphaPvr, "0.17.0.alpha-redhat-00001");

        assertThat(calculate(root, root, translations)).isEqualTo("0.17.0.redhat-00005");
        assertThat(calculate(root, alpha, translations)).isEqualTo("0.17.0.alpha-redhat-00002");
    }
}
