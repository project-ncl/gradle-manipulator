package org.jboss.gm.analyzer.alignment;

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.Project;
import org.jboss.gm.common.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This is what {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME} task uses to retrieve a fully wired
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService}
 */
public final class AlignmentServiceFactory {

    private AlignmentServiceFactory() {
    }

    public static AlignmentService getAlignmentService(Project project) {
        final Configuration configuration = ConfigCache.getOrCreate(Configuration.class);
        final String daEndpointUrl = configuration.daEndpoint();
        if (daEndpointUrl == null) {
            throw new IllegalArgumentException(
                    String.format("'%s' must be configured in order for alignment to work", Configuration.DA));
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
                new UpdateProjectVersionCustomizer(projectVersionRef, configuration));
    }
}
