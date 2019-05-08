package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.ProjectVersionFactory.withGAV;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aeonbits.owner.ConfigFactory;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.InvalidUserDataException;
import org.jboss.gm.common.Configuration;
import org.junit.Test;

public class DependencyOverrideCustomizerFromConfigurationAndModuleTest {

    private static final ProjectVersionRef PROJECT = withGAV("org.acme", "test", "1.0.0-redhat-00001");

    @Test
    public void noDependencyOverrideProperty() {
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final AlignmentService.ResponseCustomizer sut = DependencyOverrideCustomizer.fromConfigurationForModule(configuration,
                PROJECT);

        assertThat(sut).isSameAs(AlignmentService.ResponseCustomizer.NOOP);
    }

    @Test(expected = InvalidUserDataException.class)
    public void erroneousPropertiesCauseFailure() {
        System.setProperty("dependencyOverride.org.acme", "");
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        DependencyOverrideCustomizer.fromConfigurationForModule(configuration, PROJECT);
    }

    @Test
    public void ensureOverrideMatches() {
        final ProjectVersionRef hibernateCoreGav = withGAV("org.hibernate", "hibernate-core",
                "5.3.9.Final-redhat-00001");
        final ProjectVersionRef hibernateValidatorGav = withGAV("org.hibernate", "hibernate-validator",
                "6.0.16.Final-redhat-00001");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core",
                "2.0.15.Final-redhat-00001");
        final ProjectVersionRef jacksonCoreGav = withGAV("com.fasterxml.jackson.core", "jackson-core",
                "2.9.8-redhat-00001");
        final ProjectVersionRef jacksonMapperGav = withGAV("com.fasterxml.jackson.core", "jackson-mapper",
                "2.9.8-redhat-00001");
        final ProjectVersionRef mongoGav = withGAV("org.mongodb", "mongo-java-driver", "3.10.2-redhat-00001");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0-redhat-00001");
        final ProjectVersionRef wiremockGav = withGAV("com.github.tomakehurst", "wiremock-jre8",
                "2.23.2-redhat-00001");

        System.setProperty("dependencyOverride.org.hibernate:hibernate-core@*",
                "5.3.7.Final-redhat-00001"); // should result in overriding only hibernate-core dependency
        System.setProperty("dependencyOverride.com.fasterxml.jackson.core:*@*",
                "2.9.5-redhat-00001"); // should result in overriding all jackson dependencies
        System.setProperty("dependencyOverride.io.undertow:undertow-servlet@*",
                "2.0.14.Final-redhat-00001"); // should NOT result in overriding the undertow dependency since the artifact doesn't match
        System.setProperty("dependencyOverride.org.mockito:*@org.acme:test",
                "2.27.0-redhat-00002"); // should result in overriding the mockito dependency
        System.setProperty("dependencyOverride.com.github.tomakehurst:*@org.acme:other",
                ""); // should NOT result overriding the wiremock dependency since the module doesn't match

        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final AlignmentService.ResponseCustomizer sut = DependencyOverrideCustomizer.fromConfigurationForModule(configuration,
                PROJECT);

        final AlignmentService.Response originalResp = new DummyResponse(PROJECT,
                Arrays.asList(hibernateCoreGav, hibernateValidatorGav, undertowGav, jacksonCoreGav, jacksonMapperGav,
                        mongoGav, mockitoGav, wiremockGav));
        final AlignmentService.Response finalResp = sut.customize(originalResp);

        assertThat(finalResp).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isEqualTo(PROJECT.getVersionString());
            assertThat(r.getAlignedVersionOfGav(hibernateCoreGav)).isEqualTo("5.3.7.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(hibernateValidatorGav)).isEqualTo(hibernateValidatorGav.getVersionString());
            assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo(undertowGav.getVersionString());
            assertThat(r.getAlignedVersionOfGav(jacksonCoreGav)).isEqualTo("2.9.5-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(mockitoGav)).isEqualTo("2.27.0-redhat-00002");
            assertThat(r.getAlignedVersionOfGav(wiremockGav)).isEqualTo(wiremockGav.getVersionString());
        });
    }

    private static class DummyResponse implements AlignmentService.Response {
        private final ProjectVersionRef project;
        private final Map<ProjectVersionRef, String> alignedVersionsMap = new HashMap<>();

        DummyResponse(ProjectVersionRef project, List<? extends ProjectVersionRef> dependencies) {
            this.project = project;
            dependencies.forEach(d -> {
                this.alignedVersionsMap.put(d, d.getVersionString());
            });
        }

        @Override
        public String getNewProjectVersion() {
            return project.getVersionString();
        }

        @Override
        public String getAlignedVersionOfGav(ProjectVersionRef gav) {
            return alignedVersionsMap.get(gav);
        }
    }
}
