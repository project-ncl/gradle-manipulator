package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jboss.gm.common.alignment.AlignmentModel;

/**
 * Fixes pom.xml generation in "maven-publish" plugin.
 *
 * Applies PomTransformer, that overrides dependencies versions according to given configuration, to all maven
 * publications.
 */
public class PublicationPomTransformerAction implements Action<Project> {

    private AlignmentModel.Module alignmentConfiguration;

    public PublicationPomTransformerAction(AlignmentModel.Module alignmentConfiguration) {
        this.alignmentConfiguration = alignmentConfiguration;
    }

    @Override
    public void execute(Project project) {
        project.getPlugins().withType(MavenPublishPlugin.class,
                plugin -> project.getExtensions().configure(PublishingExtension.class, extension -> {
                    NamedDomainObjectSet<MavenPublication> mavenPublications = extension.getPublications()
                            .withType(MavenPublication.class);
                    mavenPublications.all(maven -> {
                        if (maven.getPom() != null) {
                            maven.getPom().withXml(new PomTransformer(alignmentConfiguration));
                        }
                    });
                }));
    }
}
