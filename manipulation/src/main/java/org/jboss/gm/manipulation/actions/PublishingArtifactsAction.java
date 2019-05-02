package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

/**
 * <p>
 * Configures maven-publish plugin publications.
 * </p>
 *
 * <p>
 * Equivalent to following gradle snippet:
 * </p>
 *
 * <pre>
 * publishing {
 *   publications {
 *     mavenJava(MavenPublication) {
 *       from components.java
 *     }
 *   }
 * }
 * </pre>
 */
public class PublishingArtifactsAction implements Action<Project> {
    @Override
    public void execute(Project project) {
        project.getExtensions().configure(PublishingExtension.class, new Action<PublishingExtension>() {
            @Override
            public void execute(PublishingExtension publishingExtension) {
                publishingExtension.publications(new Action<PublicationContainer>() {
                    @Override
                    public void execute(PublicationContainer publications) {
                        publications.create("mavenJava", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication mavenPublication) {
                                mavenPublication.from(project.getComponents().getByName("java"));
                                // TODO: why are optional dependencies missing in generated pom.xml?
                            }
                        });
                    }
                });
            }
        });
    }
}
