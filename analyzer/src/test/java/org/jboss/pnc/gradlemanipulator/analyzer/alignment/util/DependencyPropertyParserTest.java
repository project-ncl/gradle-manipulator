package org.jboss.pnc.gradlemanipulator.analyzer.alignment.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.InvalidUserDataException;
import org.junit.Test;

/**
 * Characterization tests for {@link DependencyPropertyParser}.
 */
public class DependencyPropertyParserTest {

    // ---------------------------------------------------------------------------
    // matchesAllModules() — global vs. scoped selectors
    // ---------------------------------------------------------------------------

    @Test
    public void globalModuleSelectorIsDetected() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("*:*@*");
        assertThat(result.matchesAllModules()).isTrue();
    }

    @Test
    public void groupWildcardModuleSelectorIsNotGlobal() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("junit:junit@org.acme:*");
        assertThat(result.matchesAllModules()).isFalse();
    }

    @Test
    public void specificModuleSelectorIsNotGlobal() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("junit:junit@org.acme:test");
        assertThat(result.matchesAllModules()).isFalse();
    }

    // ---------------------------------------------------------------------------
    // matchesModule() — global selector matches every ProjectRef
    // ---------------------------------------------------------------------------

    @Test
    public void globalSelectorMatchesConcreteModule() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("junit:junit@*");
        final SimpleProjectVersionRef concreteModule = new SimpleProjectVersionRef("org.acme", "test", "1.0");
        assertThat(result.matchesModule(concreteModule)).isTrue();
    }

    @Test
    public void globalSelectorMatchesAnotherConcreteModule() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("*:*@*");
        final SimpleProjectVersionRef concreteModule = new SimpleProjectVersionRef(
                "com.example",
                "my-module",
                "2.3.4");
        assertThat(result.matchesModule(concreteModule)).isTrue();
    }

    // ---------------------------------------------------------------------------
    // getDependency() — dependency selector parsing
    // ---------------------------------------------------------------------------

    @Test
    public void wildcardDependencyGroupAndArtifact() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("*:*@*");
        assertThat(result.getDependency().getGroupId()).isEqualTo("*");
        assertThat(result.getDependency().getArtifactId()).isEqualTo("*");
    }

    @Test
    public void groupWildcardDependencySelector() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("org.hibernate:*@*");
        assertThat(result.getDependency().getGroupId()).isEqualTo("org.hibernate");
        assertThat(result.getDependency().getArtifactId()).isEqualTo("*");
    }

    @Test
    public void specificDependencySelector() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse(
                "junit:junit@org.acme:test");
        assertThat(result.getDependency()).isEqualTo(SimpleProjectRef.parse("junit:junit"));
    }

    // ---------------------------------------------------------------------------
    // Wildcard dependency matching via ProjectRef.matches()
    // ---------------------------------------------------------------------------

    @Test
    public void wildcardGroupMatchesAnyGroup() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("*:junit@*");
        final SimpleProjectVersionRef concreteDep = new SimpleProjectVersionRef("junit", "junit", "4.12");
        // Atlas '*' group matches everything
        assertThat(result.getDependency().matches(concreteDep)).isTrue();
    }

    @Test
    public void wildcardArtifactMatchesAnyArtifact() {
        final DependencyPropertyParser.Result result = DependencyPropertyParser.parse("junit:*@*");
        final SimpleProjectVersionRef concreteDep = new SimpleProjectVersionRef("junit", "junit", "4.12");
        assertThat(result.getDependency().matches(concreteDep)).isTrue();
    }

    // ---------------------------------------------------------------------------
    // Malformed key — must throw
    // ---------------------------------------------------------------------------

    @Test
    public void missingAtSignThrows() {
        assertThatThrownBy(() -> DependencyPropertyParser.parse("junit:junit"))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    public void missingColonInDependencySelectorThrows() {
        assertThatThrownBy(() -> DependencyPropertyParser.parse("junit@*"))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    public void multipleAtSignsThrow() {
        assertThatThrownBy(() -> DependencyPropertyParser.parse("junit:junit@org.acme@test"))
                .isInstanceOf(InvalidUserDataException.class);
    }
}
