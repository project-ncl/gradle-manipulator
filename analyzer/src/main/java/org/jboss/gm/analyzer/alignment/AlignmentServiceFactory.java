package org.jboss.gm.analyzer.alignment;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.jboss.gm.analyzer.alignment.AlignmentService.RequestCustomizer;
import org.jboss.gm.analyzer.alignment.AlignmentService.ResponseCustomizer;
import org.jboss.gm.common.Configuration;

/**
 * This is what {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME} task uses to retrieve a fully wired
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService}
 */
final class AlignmentServiceFactory {

    private AlignmentServiceFactory() {
    }

    static AlignmentService getAlignmentService(Configuration configuration, Set<Project> projects) {
        return new WithCustomizersDelegatingAlignmentService(new DAAlignmentService(configuration),
                getRequestCustomizers(configuration, projects),
                getResponseCustomizers(configuration, projects));
    }

    /**
     * Creates the request customizers.
     * Currently only a single one exists so in theory we could eliminate the List here. Keeping it for consistency
     * with the response side and potential future expansion.
     * 
     * @param configuration the current Configuration.
     * @param projects the current Projects
     * @return the list of Request Customizers.
     */
    private static List<RequestCustomizer> getRequestCustomizers(Configuration configuration,
            Set<Project> projects) {
        return Stream.of(DependencyExclusionCustomizer.fromConfigurationForModule(configuration,
                projects)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static List<ResponseCustomizer> getResponseCustomizers(Configuration configuration,
            Set<Project> projects) {
        return Stream.of(DependencyOverrideCustomizer.fromConfigurationForModule(configuration, projects),
                new UpdateProjectVersionCustomizer(projects, configuration)).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
