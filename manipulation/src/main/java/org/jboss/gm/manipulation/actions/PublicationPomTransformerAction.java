package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jboss.gm.common.alignment.Project;

/**
 * Fixes pom.xml generation in "maven-publish" plugin.
 * <p>
 * Applies PomTransformer, that overrides dependencies versions according to given configuration, to all maven
 * publications.
 */
public class PublicationPomTransformerAction implements Action<org.gradle.api.Project> {

    private Project.Module alignmentConfiguration;

    public PublicationPomTransformerAction(Project.Module alignmentConfiguration) {
        this.alignmentConfiguration = alignmentConfiguration;
    }

    @Override
    public void execute(org.gradle.api.Project project) {
        project.getPlugins().withType(MavenPublishPlugin.class,
                plugin -> project.getExtensions().configure(PublishingExtension.class, extension -> {
                    extension.getPublications().withType(MavenPublication.class).all(maven -> {
                        if (maven.getPom() != null) {
                            maven.getPom().withXml(new PomTransformer(alignmentConfiguration));
                        }
                    });
                }));
    }
}
