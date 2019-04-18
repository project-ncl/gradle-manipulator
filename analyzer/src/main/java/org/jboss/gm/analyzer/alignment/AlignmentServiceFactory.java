package org.jboss.gm.analyzer.alignment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.Project;

public final class AlignmentServiceFactory {

    private AlignmentServiceFactory() {
    }

    public static AlignmentService getAlignmentService(Project project) {
        final Configuration configuration = ConfigurationFactory.getConfiguration();
        final String daEndpointUrl = configuration.getString("da.endpoint.url");
        if (daEndpointUrl == null) {
            throw new IllegalArgumentException("'da.endpointURL' must be configured in order for alignment to work");
        }

        final ProjectVersionRef projectVersionRef = new SimpleProjectVersionRef(project.getGroup().toString(),
                project.getName(), project.getVersion().toString());
        return new WithCustomizersDelegatingAlignmentService(new DAAlignmentService(daEndpointUrl),
                getRequestCustomizers(configuration, projectVersionRef),
                getResponseCustomizers(configuration, projectVersionRef));
    }

    private static List<AlignmentService.RequestCustomizer> getRequestCustomizers(Configuration configuration,
            ProjectVersionRef projectVersionRef) {
        return Collections
                .singletonList(DependencyExclusionCustomizer.fromConfigurationForModule(configuration, projectVersionRef));
    }

    private static List<AlignmentService.ResponseCustomizer> getResponseCustomizers(Configuration configuration,
            ProjectVersionRef projectVersionRef) {
        return Arrays.asList(DependencyOverrideCustomizer.fromConfigurationForModule(configuration, projectVersionRef),
                new UpdateProjectVersionCustomizer(projectVersionRef.getVersionString(), configuration.getString("suffix.name"),
                        configuration.getInt("suffix.padding.count")));
    }
}
