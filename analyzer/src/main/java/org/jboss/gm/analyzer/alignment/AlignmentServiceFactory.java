package org.jboss.gm.analyzer.alignment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.Project;
import org.jboss.gm.common.Configuration;

/**
 * This is what {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME} task uses to retrieve a fully wired
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService}
 */
final class AlignmentServiceFactory {

    private AlignmentServiceFactory() {
    }

    static AlignmentService getAlignmentService(Set<Project> projects) {
        Configuration configuration = ConfigCache.getOrCreate(Configuration.class);

        return new WithCustomizersDelegatingAlignmentService(new DAAlignmentService(configuration),
                getRequestCustomizers(configuration, projects),
                getResponseCustomizers(configuration, projects));
    }

    private static List<AlignmentService.RequestCustomizer> getRequestCustomizers(Configuration configuration,
            Set<Project> projects) {
        return Collections
                .singletonList(DependencyExclusionCustomizer.fromConfigurationForModule(configuration,
                        projects));
    }

    private static List<AlignmentService.ResponseCustomizer> getResponseCustomizers(Configuration configuration,
            Set<Project> projects) {
        return Arrays.asList(
                DependencyOverrideCustomizer.fromConfigurationForModule(configuration, projects),
                new UpdateProjectVersionCustomizer(projects, configuration),
                new EnsureDynamicDependenciesIncludedCustomizer());
    }
}
