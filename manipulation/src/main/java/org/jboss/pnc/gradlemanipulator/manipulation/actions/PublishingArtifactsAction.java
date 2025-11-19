package org.jboss.pnc.gradlemanipulator.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;

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

    private final Logger logger = GMLogger.getLogger(getClass());

    @Override
    public void execute(Project project) {
        try {
            SoftwareComponent javaComponent = (SoftwareComponent) project.getComponents().named("java");
            project.getExtensions()
                    .configure(
                            PublishingExtension.class,
                            publishingExtension -> publishingExtension.publications(publications -> {
                                publications.register(
                                        "mavenJava",
                                        MavenPublication.class,
                                        mavenPublication -> mavenPublication.from(javaComponent));
                            }));
        } catch (UnknownDomainObjectException e) {
            logger.warn("No java component found for project {}, no artifact to publish.", project.getName());
        }
    }
}
