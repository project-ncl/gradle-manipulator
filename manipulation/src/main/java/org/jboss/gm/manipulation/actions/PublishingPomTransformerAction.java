package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * Fixes pom.xml generation in "maven-publish" plugin.
 * <p>
 * Applies PomTransformer, that overrides dependencies versions according to given configuration, to all maven
 * publications.
 */
public class PublishingPomTransformerAction implements Action<Project> {

    private final ManipulationModel alignmentConfiguration;
    private final ResolvedDependenciesRepository resolvedDependenciesRepository;

    public PublishingPomTransformerAction(ManipulationModel alignmentConfiguration,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.alignmentConfiguration = alignmentConfiguration;
        this.resolvedDependenciesRepository = resolvedDependenciesRepository;
    }

    @Override
    public void execute(Project project) {
        if (!project.getPluginManager().hasPlugin("maven-publish")) {
            return;
        }

        project.getExtensions().getByType(PublishingExtension.class).getPublications().withType(MavenPublication.class)
                .all(maven -> {
                    if (maven.getPom() != null) {
                        maven.getPom().withXml(new PomTransformer(alignmentConfiguration, resolvedDependenciesRepository));
                    }
                });
    }
}
