package org.jboss.gm.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.logging.LogLevel;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import uk.org.webcompere.systemstubs.rules.SystemErrRule;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class ManipulationModelTest {

    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule();

    @Test
    public void findCorrespondingChildWithName() {
        ManipulationModel model = new ManipulationModel("root", "root", "bar");
        final ManipulationModel child = new ManipulationModel("child1", "child1", "bar");
        model.addChild(child);
        model.addChild(new ManipulationModel("child2", "child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "child11-custom-artifactId", "bar");
        child.addChild(child11);

        assertThat(model.findCorrespondingChild(model.getName())).isEqualTo(model);
        assertThat(model.findCorrespondingChild("child1")).isEqualTo(child);

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> model.findCorrespondingChild("child11"))
                .withMessage("ManipulationModel 'child11' does not exist");

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> model.findCorrespondingChild(""))
                .withMessage("Supplied child name cannot be empty");

        assertThat(child.findCorrespondingChild("child11")).isEqualTo(child11);
    }

    @Test
    public void findCorrespondingChildWithPath() {
        ManipulationModel model = new ManipulationModel("root", "root", "bar");
        final ManipulationModel child = new ManipulationModel("child1", "child1", "bar");
        model.addChild(child);
        model.addChild(new ManipulationModel("child2", "child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "child11", "bar");
        child.addChild(child11);
        final ManipulationModel child111 = new ManipulationModel("child111", "child111", "bar");
        child11.addChild(child111);

        assertThat(model.findCorrespondingChild(":")).isEqualTo(model);

        assertThat(model.findCorrespondingChild(":child1")).isEqualTo(child);

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> model.findCorrespondingChild(":child11"))
                .withMessage("ManipulationModel 'child11' does not exist");

        assertThat(child.findCorrespondingChild(":child11")).isEqualTo(child11);
        assertThat(model.findCorrespondingChild(":child2").name).isEqualTo("child2");
        assertThat(model.findCorrespondingChild(":child1:child11:child111")).isEqualTo(child111);
    }

    @Test
    public void getAllAlignedDependencies() {
        final ManipulationModel root = new ManipulationModel("root", "root", "bar");
        root.getAlignedDependencies()
                .put(
                        "org.jboss.resteasy:resteasy-jaxrs:3.6.3.Final",
                        new SimpleProjectVersionRef(
                                "org.jboss.resteasy",
                                "resteasy-jaxrs",
                                "3.6.3.Final-redhat-000001"));
        final ManipulationModel child = new ManipulationModel("child1", "child1", "bar");
        child.getAlignedDependencies()
                .put(
                        "org.hibernate:hibernate-core:5.3.7.Final",
                        new SimpleProjectVersionRef("org.hibernate", "hibernate-core", "5.3.7.Final-redhat-000001"));
        root.addChild(child);
        root.addChild(new ManipulationModel("child2", "child2", "bar"));
        final ManipulationModel child11 = new ManipulationModel("child11", "child11", "bar");
        child.addChild(child11);
        child11.getAlignedDependencies()
                .put(
                        "io.undertow:undertow-core:2.0.15.Final",
                        new SimpleProjectVersionRef("io.undertow", "undertow-core", "2.0.15.Final-redhat-000001"));
        child11.getAlignedDependencies()
                .put(
                        "org.mockito:mockito-core:2.27.0",
                        new SimpleProjectVersionRef("io.undertow", "undertow-core", "2.27.0-redhat-000001"));

        assertThat(root.getAllAlignedDependencies()).containsOnlyKeys(
                "org.jboss.resteasy:resteasy-jaxrs:3.6.3.Final",
                "org.hibernate:hibernate-core:5.3.7.Final",
                "io.undertow:undertow-core:2.0.15.Final",
                "org.mockito:mockito-core:2.27.0")
                .satisfies(
                        m -> assertThat(m.values()).containsOnly(
                                new SimpleProjectVersionRef(
                                        "org.hibernate",
                                        "hibernate-core",
                                        "5.3.7.Final-redhat-000001"),
                                new SimpleProjectVersionRef(
                                        "org.jboss.resteasy",
                                        "resteasy-jaxrs",
                                        "3.6.3.Final-redhat-000001"),
                                new SimpleProjectVersionRef(
                                        "io.undertow",
                                        "undertow-core",
                                        "2.0.15.Final-redhat-000001"),
                                new SimpleProjectVersionRef("io.undertow", "undertow-core", "2.27.0-redhat-000001")));
    }

    @Test
    public void findCorrespondingChildWithDuplicateName() {
        ManipulationModel model = new ManipulationModel("root-foo", "foo", "org.hibernate");
        final ManipulationModel child = new ManipulationModel("foo", "foo", "org.hibernate");
        model.addChild(child);
        model.addChild(new ManipulationModel("child2", "child2", "childgroup2"));
        final ManipulationModel child11 = new ManipulationModel("child11", "child11-custom-artifactId", "bar");
        child.addChild(child11);

        assertNotEquals(System.identityHashCode(model.findCorrespondingChild(":foo")), System.identityHashCode(model));
        assertEquals(System.identityHashCode(model.findCorrespondingChild(":foo")), System.identityHashCode(child));
        assertThat(model.findCorrespondingChild(":foo:child11")).isEqualTo(child11);
        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> child.findCorrespondingChild(":foo:child11"))
                .withMessage("ManipulationModel 'foo' does not exist");

        // So we should never hit this - normally findCorrespondingChild should be called through the Project API which means children
        // have correctly colon separated paths.
        assertEquals(System.identityHashCode(model.findCorrespondingChild("foo")), System.identityHashCode(model));
        assertTrue(
                systemErrRule.getLinesNormalized()
                        .contains(
                                "Child module ([child2, foo]) has matching name to current module (foo) and unable to differentiate"));
    }
}
