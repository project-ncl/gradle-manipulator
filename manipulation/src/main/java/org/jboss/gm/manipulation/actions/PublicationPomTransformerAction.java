package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jboss.gm.common.alignment.Module;

/**
 * Fixes pom.xml generation in "maven-publish" plugin.
 * <p>
 * Applies PomTransformer, that overrides dependencies versions according to given configuration, to all maven
 * publications.
 */
public class PublicationPomTransformerAction implements Action<Project> {

    private Module alignmentConfiguration;

    public PublicationPomTransformerAction(Module alignmentConfiguration) {
        this.alignmentConfiguration = alignmentConfiguration;
    }

    @Override
    public void execute(Project project) {
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
