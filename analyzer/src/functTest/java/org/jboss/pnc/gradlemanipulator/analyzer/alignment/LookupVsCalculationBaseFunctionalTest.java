package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.TestUtils.TestManipulationModel;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.model.ManipulationModel;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;
import uk.org.webcompere.systemstubs.rules.SystemPropertiesRule;

/**
 * NCLSUP-1773: the REST lookup and the version calculation must use the <em>same</em> base version.
 *
 * <p>
 * See {@code NCLSUP1773_LOOKUP_VS_CALCULATION_BASE_PLAN.md} section 5.1 for the scenario matrix. Scenario ids
 * (A1, B3, C5, ...) are used in the test names.
 *
 * <p>
 * Unlike the other functional tests the DA stub here is <em>request aware</em>: the {@code LOOKUP_LATEST} response is
 * only returned when the request carries every expected {@code group:artifact @ version}; any other request gets the
 * catch-all {@code []} that real DA returns when it has no builds for the requested base. A wrong lookup version is
 * therefore noticed, whereas an unconditional stub would hide it.
 *
 * <ul>
 * <li>Layout M ("names match"): the {@code enforce-version-prefix} fixture (root {@code root}) with its
 * {@code gradle.properties} rewritten.</li>
 * <li>Layout X ("name mismatch"): the {@code name-mismatch-multi-module} fixture - unpublished root
 * {@code solr-root}, Gradle project {@code core} published as {@code solr-core}, {@code util} as
 * {@code solr-util}.</li>
 * </ul>
 */
public class LookupVsCalculationBaseFunctionalTest extends AbstractWiremockTest {

    private static final String GROUP = "org.apache.solr";
    private static final String LOOKUP_LATEST_URL = "/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST;
    private static final String ORIGINAL = "10.0.0";

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Rule
    public final TestRule restoreSystemProperties = new SystemPropertiesRule();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() {
        stubFor(
                post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody("[]")));
        System.setProperty(Configuration.DA, "http://127.0.0.1:" + wireMockRule.port() + "/da/rest/v-1");
    }

    // ------------------------------------------------------------------------------------------
    // A. No manipulation.json - baseline characterisation (expected to PASS on current code)
    // ------------------------------------------------------------------------------------------

    @Test
    public void A1_noJson_namesMatch_defaultSuffix_noPriorBuild() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0"), map());
        final TestManipulationModel m = alignM("10.0.0", null, props(null, null));

        assertRequests(map("org.apache.solr:root", "10.0.0"));
        assertAllNodes(m, "10.0.0.redhat-00001", ORIGINAL);
    }

    @Test
    public void A2_noJson_namesMatch_defaultSuffix_priorBuild() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0"), map("org.apache.solr:root", "10.0.0.redhat-00003"));
        final TestManipulationModel m = alignM("10.0.0", null, props(null, null));

        assertRequests(map("org.apache.solr:root", "10.0.0"));
        assertAllNodes(m, "10.0.0.redhat-00004", ORIGINAL);
        assertCounterAbove(m.getVersion(), 3);
    }

    @Test
    public void A3_noJson_namesMatch_evpAndN_noPriorBuild() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0.rhlw-00000"), map());
        final TestManipulationModel m = alignM("10.0.0", null, props("rhlw", "n"));

        assertRequests(map("org.apache.solr:root", "10.0.0.rhlw-00000"));
        assertAllNodes(m, "10.0.0.rhlw-00000-n-00001", ORIGINAL);
    }

    @Test
    public void A4_noJson_namesMatch_evpAndN_priorBuild() throws Exception {
        stubDa(
                map("org.apache.solr:root", "10.0.0.rhlw-00000"),
                map("org.apache.solr:root", "10.0.0.rhlw-00000-n-00011"));
        final TestManipulationModel m = alignM("10.0.0", null, props("rhlw", "n"));

        assertRequests(map("org.apache.solr:root", "10.0.0.rhlw-00000"));
        assertAllNodes(m, "10.0.0.rhlw-00000-n-00012", ORIGINAL);
        assertCounterAbove(m.getVersion(), 11);
    }

    @Test
    public void A5_noJson_nameMismatch_evpAndN_noPriorBuild() throws Exception {
        final Map<String, String> requests = map(
                GROUP + ":solr-core",
                "10.0.0.rhlw-00000",
                GROUP + ":solr-util",
                "10.0.0.rhlw-00000");
        stubDa(requests, map());
        final TestManipulationModel m = alignX(null, props("rhlw", "n"));

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.rhlw-00000-n-00001", ORIGINAL);
    }

    @Test
    public void A6_noJson_nameMismatch_evpAndN_pooledMax() throws Exception {
        final Map<String, String> requests = map(
                GROUP + ":solr-core",
                "10.0.0.rhlw-00000",
                GROUP + ":solr-util",
                "10.0.0.rhlw-00000");
        stubDa(
                requests,
                map(
                        GROUP + ":solr-core",
                        "10.0.0.rhlw-00000-n-00011",
                        GROUP + ":solr-util",
                        "10.0.0.rhlw-00000-n-00004"));
        final TestManipulationModel m = alignX(null, props("rhlw", "n"));

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.rhlw-00000-n-00012", ORIGINAL);
        assertCounterAbove(m.getVersion(), 11);
    }

    @Test
    public void A7_noJson_nameMismatch_defaultSuffix_pooledMax() throws Exception {
        final Map<String, String> requests = map(GROUP + ":solr-core", "10.0.0", GROUP + ":solr-util", "10.0.0");
        stubDa(requests, map(GROUP + ":solr-core", "10.0.0.redhat-00003"));
        final TestManipulationModel m = alignX(null, props(null, null));

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.redhat-00004", ORIGINAL);
        assertCounterAbove(m.getVersion(), 3);
    }

    @Test
    public void A8_noJson_namesMatch_snapshot_suffixSnapshotTrue() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0-SNAPSHOT"), map("org.apache.solr:root", "10.0.0.redhat-00001"));
        final Map<String, String> p = props(null, null);
        p.put("versionSuffixSnapshot", "true");
        final TestManipulationModel m = alignM("10.0.0-SNAPSHOT", null, p);

        assertRequests(map("org.apache.solr:root", "10.0.0-SNAPSHOT"));
        assertAllNodes(m, "10.0.0.redhat-00002-SNAPSHOT", "10.0.0-SNAPSHOT");
    }

    @Test
    public void A8_noJson_namesMatch_snapshot_suffixSnapshotFalse() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0"), map("org.apache.solr:root", "10.0.0.redhat-00001"));
        final Map<String, String> p = props(null, null);
        p.put("versionSuffixSnapshot", "false");
        final TestManipulationModel m = alignM("10.0.0-SNAPSHOT", null, p);

        assertRequests(map("org.apache.solr:root", "10.0.0"));
        assertAllNodes(m, "10.0.0.redhat-00002", "10.0.0-SNAPSHOT");
    }

    // ------------------------------------------------------------------------------------------
    // B. With manipulation.json, names match (expected to PASS today)
    // ------------------------------------------------------------------------------------------

    @Test
    public void B1_json_namesMatch_evpAndN_daEmpty() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0.rhlw-00003"), map());
        final TestManipulationModel m = alignM("10.0.0", "10.0.0.rhlw-00003", props("rhlw", "n"));

        assertRequests(map("org.apache.solr:root", "10.0.0.rhlw-00003"));
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00001", ORIGINAL);
    }

    @Test
    public void B2_json_namesMatch_evpAndN_daHasPriorN() throws Exception {
        stubDa(
                map("org.apache.solr:root", "10.0.0.rhlw-00003"),
                map("org.apache.solr:root", "10.0.0.rhlw-00003-n-00004"));
        final TestManipulationModel m = alignM("10.0.0", "10.0.0.rhlw-00003", props("rhlw", "n"));

        assertRequests(map("org.apache.solr:root", "10.0.0.rhlw-00003"));
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00005", ORIGINAL);
        assertCounterAbove(m.getVersion(), 4);
    }

    @Test
    public void B3_json_namesMatch_jsonAlreadyHasN_counterContinues() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0.rhlw-00003-n-00002"), map());
        final TestManipulationModel m = alignM("10.0.0", "10.0.0.rhlw-00003-n-00002", props("rhlw", "n"));

        assertRequests(map("org.apache.solr:root", "10.0.0.rhlw-00003-n-00002"));
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00003", ORIGINAL);
        assertCounterAbove(m.getVersion(), 2);
    }

    @Test
    public void B4_json_namesMatch_defaultSuffixOnTopOfRhlw() throws Exception {
        stubDa(map("org.apache.solr:root", "1.1.0.rhlw-00001"), map());
        final TestManipulationModel m = alignM("1.1.0", "1.1.0.rhlw-00001", props(null, "redhat"));

        assertRequests(map("org.apache.solr:root", "1.1.0.rhlw-00001"));
        assertAllNodes(m, "1.1.0.rhlw-00001-redhat-00001", "1.1.0");
    }

    @Test
    public void B5_json_namesMatch_sameSuffixIncrements() throws Exception {
        stubDa(map("org.apache.solr:root", "10.0.0.rhlw-00003"), map("org.apache.solr:root", "10.0.0.rhlw-00003"));
        final TestManipulationModel m = alignM("10.0.0", "10.0.0.rhlw-00003", props(null, "rhlw"));

        assertRequests(map("org.apache.solr:root", "10.0.0.rhlw-00003"));
        assertAllNodes(m, "10.0.0.rhlw-00004", ORIGINAL);
        assertCounterAbove(m.getVersion(), 3);
    }

    @Test
    public void B6_json_namesMatch_olderLineageDoesNotAdvanceCounter() throws Exception {
        stubDa(
                map("org.apache.solr:root", "10.0.0.rhlw-00003"),
                map("org.apache.solr:root", "10.0.0.rhlw-00000-n-00016"));
        final TestManipulationModel m = alignM("10.0.0", "10.0.0.rhlw-00003", props("rhlw", "n"));

        assertRequests(map("org.apache.solr:root", "10.0.0.rhlw-00003"));
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00001", ORIGINAL);
    }

    // ------------------------------------------------------------------------------------------
    // C. With manipulation.json, names do NOT match - the NCLSUP-1773 bug (expected to FAIL today)
    // ------------------------------------------------------------------------------------------

    @Test
    public void C1_json_nameMismatch_evpAndN_daEmpty() throws Exception {
        final Map<String, String> requests = coreUtilRequests("10.0.0.rhlw-00003", "10.0.0.rhlw-00003");
        stubDa(requests, map());
        final TestManipulationModel m = alignX(
                new XJson("10.0.0.rhlw-00003", "10.0.0.rhlw-00003", "10.0.0.rhlw-00003"),
                props("rhlw", "n"));

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00001", ORIGINAL);
    }

    @Test
    public void C2_json_nameMismatch_evpAndN_daHasPriorN() throws Exception {
        final Map<String, String> requests = coreUtilRequests("10.0.0.rhlw-00003", "10.0.0.rhlw-00003");
        stubDa(requests, map(GROUP + ":solr-core", "10.0.0.rhlw-00003-n-00004"));
        final TestManipulationModel m = alignX(
                new XJson("10.0.0.rhlw-00003", "10.0.0.rhlw-00003", "10.0.0.rhlw-00003"),
                props("rhlw", "n"));

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00005", ORIGINAL);
        assertCounterAbove(m.getVersion(), 4);
    }

    @Test
    public void C3_json_nameMismatch_noEvp_defaultStyleSuffix() throws Exception {
        final Map<String, String> requests = coreUtilRequests("10.0.0.rhlw-00003", "10.0.0.rhlw-00003");
        stubDa(requests, map());
        final TestManipulationModel m = alignX(
                new XJson("10.0.0.rhlw-00003", "10.0.0.rhlw-00003", "10.0.0.rhlw-00003"),
                props(null, "redhat"));

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.rhlw-00003-redhat-00001", ORIGINAL);
    }

    @Test
    public void C4_json_unpublishedRoot_singlePublishedChild() throws Exception {
        final Map<String, String> requests = map(GROUP + ":solr-core", "10.0.0.rhlw-00003");
        stubDa(requests, map());
        final Map<String, String> p = props("rhlw", "n");
        p.put("onlyCore", "true");
        final TestManipulationModel m = alignX(new XJson("10.0.0.rhlw-00003", "10.0.0.rhlw-00003", null), p);

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00001", ORIGINAL);
    }

    /**
     * C5: differing node versions (root 00002, core 00003, util 00005), same suffix so the counter is directly
     * visible. Invariant: no node may re-mint an existing version, i.e. every counter must exceed the highest known
     * counter (5). Exact per-node values are recorded, not asserted (decided in Stage 2).
     */
    @Test
    public void C5_json_nameMismatch_differingNodes_invariantCounterAboveHighest() throws Exception {
        final Map<String, String> requests = coreUtilRequests("10.0.0.rhlw-00003", "10.0.0.rhlw-00005");
        stubDa(requests, map());
        final TestManipulationModel m = alignX(
                new XJson("10.0.0.rhlw-00002", "10.0.0.rhlw-00003", "10.0.0.rhlw-00005"),
                props("rhlw", "rhlw"));

        assertRequests(requests);
        final Map<String, String> versions = nodeVersions(m);
        System.out.println("NCLSUP1773-RESULT C5 " + versions);
        versions.forEach((path, v) -> assertCounterAbove(path + "=" + v + " (all: " + versions + ")", v, 5));
    }

    /** C7: as C5 but a new 'n' lineage with DA reporting n-00007 for core's base request. */
    @Test
    public void C7_json_nameMismatch_differingNodes_newLineage_invariant() throws Exception {
        final Map<String, String> requests = coreUtilRequests("10.0.0.rhlw-00003", "10.0.0.rhlw-00005");
        stubDa(requests, map(GROUP + ":solr-core", "10.0.0.rhlw-00003-n-00007"));
        final TestManipulationModel m = alignX(
                new XJson("10.0.0.rhlw-00002", "10.0.0.rhlw-00003", "10.0.0.rhlw-00005"),
                props("rhlw", "n"));

        assertRequests(requests);
        final Map<String, String> versions = nodeVersions(m);
        System.out.println("NCLSUP1773-RESULT C7 " + versions);
        // Lineage isolation (NCLSUP-967 / §3.4.0): core advances in the rhlw-00003-n lineage (> 7),
        // while root and util start at n-00001 in their respective unbuilt lineages (rhlw-00002-n and rhlw-00005-n).
        assertThat(versions.get(":core")).isEqualTo("10.0.0.rhlw-00003-n-00008");
        assertThat(versions.get(":util")).isEqualTo("10.0.0.rhlw-00005-n-00001");
        assertThat(versions.get(":")).isEqualTo("10.0.0.rhlw-00002-n-00001");
    }

    /** C6: core published under a different group; proves or disproves the ticket's "group does not match" claim. */
    @Test
    public void C6_json_nameMismatch_differentPublicationGroup() throws Exception {
        final String coreGroup = "org.apache.solr.core";
        final Map<String, String> requests = map(
                coreGroup + ":solr-core",
                "10.0.0.rhlw-00003",
                GROUP + ":solr-util",
                "10.0.0.rhlw-00003");
        stubDa(requests, map());
        final Map<String, String> p = props("rhlw", "n");
        p.put("corePublicationGroup", coreGroup);
        final XJson json = new XJson("10.0.0.rhlw-00003", "10.0.0.rhlw-00003", "10.0.0.rhlw-00003");
        json.coreGroup = coreGroup;
        final TestManipulationModel m = alignX(json, p);

        assertRequests(requests);
        assertAllNodes(m, "10.0.0.rhlw-00003-n-00001", ORIGINAL);
    }

    /**
     * C6b: isolates the group claim - names match the published artifactIds, only core's group differs from the
     * Gradle group. Expected PASS today if AlignmentTask's {@code project.setGroup(publication group)} already aligns
     * the customizer's lookup; a failure would prove the ticket's "group does not match" claim.
     */
    @Test
    public void C6b_json_namesMatch_differentPublicationGroupOnly() throws Exception {
        final String coreGroup = "org.apache.solr.core";
        final Map<String, String> requests = map(
                coreGroup + ":core",
                "10.0.0.rhlw-00003",
                GROUP + ":util",
                "10.0.0.rhlw-00003");
        stubDa(requests, map());
        final Map<String, String> p = props("rhlw", "n");
        p.put("corePublicationGroup", coreGroup);
        p.put("matchNames", "true");
        final XJson json = new XJson("10.0.0.rhlw-00003", "10.0.0.rhlw-00003", "10.0.0.rhlw-00003");
        json.coreGroup = coreGroup;
        json.coreName = "core";
        json.utilName = "util";
        final TestManipulationModel m = alignX(json, p);

        assertRequests(requests);
        // The unpublished root is deliberately not asserted here: it is covered by C4 and would mask the group
        // question this test isolates.
        assertThat(m.findCorrespondingChild(":core").getVersion()).isEqualTo("10.0.0.rhlw-00003-n-00001");
        assertThat(m.findCorrespondingChild(":util").getVersion()).isEqualTo("10.0.0.rhlw-00003-n-00001");
    }

    // ------------------------------------------------------------------------------------------
    // F. NCLSUP-967 guards (multiple base versions per build must stay independent)
    // ------------------------------------------------------------------------------------------

    /**
     * F2: layout with two bases (10.0.0-alpha for core, 10.0.0 for util), no json, VIS=redhat.
     * Request carries each project at its own base; versions increment independently.
     */
    @Test
    public void F2_noJson_differentBasesInOneBuild_stayIndependent() throws Exception {
        final Map<String, String> requests = map(
                GROUP + ":solr-core",
                "10.0.0-alpha",
                GROUP + ":solr-util",
                "10.0.0");
        stubDa(
                requests,
                map(
                        GROUP + ":solr-core",
                        "10.0.0.alpha-redhat-00001",
                        GROUP + ":solr-util",
                        "10.0.0.redhat-00004"));
        final Map<String, String> p = props(null, "redhat");
        p.put("coreVersion", "10.0.0-alpha");
        final TestManipulationModel m = alignX(null, p);

        assertRequests(requests);
        assertThat(m.findCorrespondingChild(":core").getVersion()).isEqualTo("10.0.0.alpha-redhat-00002");
        assertThat(m.findCorrespondingChild(":core").getOriginalVersion()).isEqualTo("10.0.0-alpha");
        assertThat(m.findCorrespondingChild(":util").getVersion()).isEqualTo("10.0.0.redhat-00005");
        assertThat(m.findCorrespondingChild(":util").getOriginalVersion()).isEqualTo("10.0.0");
        assertThat(m.getVersion()).isEqualTo("10.0.0.redhat-00005");
        assertThat(m.getOriginalVersion()).isEqualTo("10.0.0");
    }

    /**
     * F3: layout with two bases plus manipulation.json with per-node versions, alpha module published
     * under a different artifactId. Each node continues from its own reused base without cross-lineage pollution.
     */
    @Test
    public void F3_json_differentBasesInOneBuild_stayIndependent() throws Exception {
        final Map<String, String> requests = map(
                GROUP + ":solr-core",
                "10.0.0.alpha-rhlw-00003",
                GROUP + ":solr-util",
                "10.0.0.rhlw-00004");
        stubDa(
                requests,
                map(
                        GROUP + ":solr-core",
                        "10.0.0.alpha-rhlw-00003-redhat-00005",
                        GROUP + ":solr-util",
                        "10.0.0.rhlw-00004-redhat-00008"));
        final Map<String, String> p = props(null, "redhat");
        p.put("coreVersion", "10.0.0-alpha");
        final XJson json = new XJson("10.0.0.rhlw-00001", "10.0.0.alpha-rhlw-00003", "10.0.0.rhlw-00004");
        final TestManipulationModel m = alignX(json, p);

        assertRequests(requests);
        assertThat(m.findCorrespondingChild(":core").getVersion())
                .isEqualTo("10.0.0.alpha-rhlw-00003-redhat-00006");
        assertThat(m.findCorrespondingChild(":core").getOriginalVersion()).isEqualTo("10.0.0-alpha");
        assertThat(m.findCorrespondingChild(":util").getVersion())
                .isEqualTo("10.0.0.rhlw-00004-redhat-00009");
        assertThat(m.findCorrespondingChild(":util").getOriginalVersion()).isEqualTo("10.0.0");
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures / helpers
    // ------------------------------------------------------------------------------------------

    /** Versions to write into manipulation.json for the name-mismatch fixture. */
    private static final class XJson {
        final String root;
        final String core;
        final String util;
        String coreGroup = GROUP;
        String coreName = "solr-core";
        String utilName = "solr-util";

        XJson(String root, String core, String util) {
            this.root = root;
            this.core = core;
            this.util = util;
        }
    }

    private static Map<String, String> coreUtilRequests(String coreVersion, String utilVersion) {
        return map(GROUP + ":solr-core", coreVersion, GROUP + ":solr-util", utilVersion);
    }

    private static Map<String, String> map(String... kv) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            result.put(kv[i], kv[i + 1]);
        }
        return result;
    }

    /** Alignment properties; {@code evp}/{@code vis} are omitted when null. */
    private static Map<String, String> props(String evp, String vis) {
        final Map<String, String> p = new LinkedHashMap<>();
        if (evp != null) {
            p.put("enforceVersionPrefix", evp);
        }
        if (vis != null) {
            p.put("versionIncrementalSuffix", vis);
        }
        return p;
    }

    private File copyFixture(String fixture, String folder) throws IOException, URISyntaxException {
        final URL resource = getClass().getClassLoader().getResource(fixture);
        assertThat(resource).isNotNull();
        final File root = tempDir.newFolder(folder);
        FileUtils.copyDirectory(Paths.get(resource.toURI()).toFile(), root);
        return root;
    }

    private static void write(File dir, String name, String content) throws IOException {
        Files.write(new File(dir, name).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /** Layout M: single module 'root', group org.apache.solr, no publication. */
    private TestManipulationModel alignM(String gradleVersion, String jsonVersion, Map<String, String> props)
            throws IOException, URISyntaxException {
        final File root = copyFixture("enforce-version-prefix", "layout-m");
        write(root, "gradle.properties", "group=" + GROUP + "\nversion=" + gradleVersion + "\n");
        if (jsonVersion != null) {
            write(
                    root,
                    "manipulation.json",
                    "{\n  \"group\" : \"" + GROUP + "\",\n  \"name\" : \"root\",\n  \"projectPathName\" : \"root\",\n"
                            + "  \"version\" : \"" + jsonVersion + "\",\n  \"originalVersion\" : \"" + gradleVersion
                            + "\",\n  \"alignedDependencies\" : { },\n  \"children\" : { }\n}\n");
        }
        props.put("scanProjectsWithNoPublications", "true");
        return TestUtils.align(root, false, props);
    }

    /** Layout X: unpublished root 'solr-root', 'core' published as 'solr-core', 'util' as 'solr-util'. */
    private TestManipulationModel alignX(XJson json, Map<String, String> props) throws IOException, URISyntaxException {
        final File root = copyFixture("name-mismatch-multi-module", "layout-x");
        if (json != null) {
            final StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"group\" : \"").append(GROUP).append("\",\n  \"name\" : \"solr-root\",\n");
            sb.append("  \"projectPathName\" : \"solr-root\",\n  \"version\" : \"").append(json.root).append("\",\n");
            sb.append("  \"originalVersion\" : \"").append(ORIGINAL).append("\",\n  \"alignedDependencies\" : { },\n");
            sb.append("  \"children\" : {\n");
            sb.append(child("core", json.coreGroup, json.coreName, json.core));
            if (json.util != null) {
                sb.append(",\n").append(child("util", GROUP, json.utilName, json.util));
            }
            sb.append("\n  }\n}\n");
            write(root, "manipulation.json", sb.toString());
        }
        return TestUtils.align(root, false, props);
    }

    private static String child(String path, String group, String name, String version) {
        return "    \"" + path + "\" : {\n      \"group\" : \"" + group + "\",\n      \"name\" : \"" + name
                + "\",\n      \"projectPathName\" : \"" + path + "\",\n      \"version\" : \"" + version
                + "\",\n      \"originalVersion\" : \"" + ORIGINAL + "\",\n      \"alignedDependencies\" : { },\n"
                + "      \"children\" : { }\n    }";
    }

    /**
     * Request-aware stub. The {@code latest} response is only returned when the request carries <b>every</b>
     * {@code group:artifact -> version} in {@code expectedRequests}; otherwise the catch-all {@code []} applies.
     * Response entries echo the <em>requested</em> version (that is how the translation map is keyed).
     */
    private void stubDa(Map<String, String> expectedRequests, Map<String, String> latest) {
        final StringBuilder body = new StringBuilder("[");
        latest.forEach((ga, latestVersion) -> {
            final String[] g = ga.split(":");
            if (body.length() > 1) {
                body.append(',');
            }
            body.append("{\"groupId\":\"")
                    .append(g[0])
                    .append("\",\"artifactId\":\"")
                    .append(g[1])
                    .append("\",\"version\":\"")
                    .append(expectedRequests.get(ga))
                    .append("\",\"latestVersion\":\"")
                    .append(latestVersion)
                    .append("\"}");
        });
        body.append(']');

        final MappingBuilder specific = post(urlEqualTo(LOOKUP_LATEST_URL)).atPriority(1);
        expectedRequests.forEach((ga, version) -> specific.withRequestBody(matchingJsonPath(requestPath(ga, version))));
        stubFor(
                specific.willReturn(
                        aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json;charset=utf-8")
                                .withBody(body.toString())));
        stubFor(
                post(urlEqualTo(LOOKUP_LATEST_URL)).atPriority(10)
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json;charset=utf-8")
                                        .withBody("[]")));
    }

    private static String requestPath(String ga, String version) {
        final String[] g = ga.split(":");
        return "$.artifacts[?(@.groupId=='" + g[0] + "' && @.artifactId=='" + g[1] + "' && @.version=='" + version
                + "')]";
    }

    /** Asserts the exact request GA+version was sent, and that no other version was sent for the same GA. */
    private void assertRequests(Map<String, String> expectedRequests) {
        expectedRequests.forEach((ga, version) -> {
            verify(
                    postRequestedFor(urlEqualTo(LOOKUP_LATEST_URL))
                            .withRequestBody(matchingJsonPath(requestPath(ga, version))));
            final String[] g = ga.split(":");
            verify(
                    0,
                    postRequestedFor(urlEqualTo(LOOKUP_LATEST_URL)).withRequestBody(
                            matchingJsonPath(
                                    "$.artifacts[?(@.groupId=='" + g[0] + "' && @.artifactId=='" + g[1]
                                            + "' && @.version!='" + version + "')]")));
        });
    }

    private static Map<String, String> nodeVersions(ManipulationModel root) {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put(":", root.getVersion());
        collect(root, "", result);
        return result;
    }

    private static void collect(ManipulationModel node, String prefix, Map<String, String> out) {
        node.getChildren().forEach((name, child) -> {
            out.put(prefix + ":" + name, child.getVersion());
            collect(child, prefix + ":" + name, out);
        });
    }

    /** Asserts every node (root + all descendants) has the expected version and original version. */
    private void assertAllNodes(ManipulationModel root, String expectedVersion, String expectedOriginal) {
        final Map<String, String> versions = nodeVersions(root);
        System.out.println("NCLSUP1773-RESULT " + versions);
        versions.forEach(
                (path, v) -> assertThat(v).as("version of node '%s' (all: %s)", path, versions)
                        .isEqualTo(expectedVersion));
        assertOriginal(root, expectedOriginal);
    }

    private static void assertOriginal(ManipulationModel node, String expectedOriginal) {
        assertThat(node.getOriginalVersion()).as("originalVersion of %s", node).isEqualTo(expectedOriginal);
        node.getChildren().values().forEach(c -> assertOriginal(c, expectedOriginal));
    }

    private static void assertCounterAbove(String version, int highestKnown) {
        assertCounterAbove(version, version, highestKnown);
    }

    /** Monotonic-counter invariant: a generated version must never reuse a counter already known for the lineage. */
    private static void assertCounterAbove(String label, String version, int highestKnown) {
        final Matcher m = Pattern.compile("-(\\d+)(-SNAPSHOT)?$").matcher(version);
        assertThat(m.find()).as("no trailing counter in '%s'", version).isTrue();
        assertThat(Integer.parseInt(m.group(1)))
                .as(
                        "DUPLICATE/UNDER-BUMP: '%s' does not exceed highest known counter %d for the lineage",
                        label,
                        highestKnown)
                .isGreaterThan(highestKnown);
    }
}
