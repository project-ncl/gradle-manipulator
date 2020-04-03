package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.manipulation.ResolvedDependenciesRepository;

/**
 * Fixes pom.xml generation in "maven-publish" plugin.
 * <p>
 * Applies PomTransformer, that overrides dependencies versions according to given configuration, to all maven
 * publications.
 */
public class MavenPomTransformerAction implements Action<Project> {

    private final ManipulationModel alignmentConfiguration;
    private final ResolvedDependenciesRepository resolvedDependenciesRepository;

    private final Logger logger = GMLogger.getLogger(getClass());

    public MavenPomTransformerAction(ManipulationModel alignmentConfiguration,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.alignmentConfiguration = alignmentConfiguration;
        this.resolvedDependenciesRepository = resolvedDependenciesRepository;
    }

    @Override
    public void execute(Project project) {
        if (!project.getPluginManager().hasPlugin("maven-publish")) {
            return;
        }

        // GenerateMavenPom tasks need to be postponed until after compileJava task, because that's where artifact
        // resolution is normally triggered and ResolvedDependenciesRepository is filled. If GenerateMavenPom runs
        // before compileJava, we will see empty ResolvedDependenciesRepository here.
        project.getTasks().withType(GenerateMavenPom.class).all(task -> {
            if (project.getTasks().findByName("compileJava") != null) {
                task.dependsOn("compileJava");
            }
        });

        project.getExtensions().getByType(PublishingExtension.class).getPublications()
                .withType(MavenPublication.class)
                .configureEach(publication -> {
                    logger.debug("Applying POM transformer to publication " + publication.getName());

                    if (!project.getVersion().equals(publication.getVersion())) {
                        logger.warn(
                                "Mismatch between project version ({}) and publication version ({}). Resetting to project version.",
                                project.getVersion(), publication.getVersion());
                        publication.setVersion(project.getVersion().toString());
                    }
                    if (publication.getPom() != null) {
                        publication.getPom()
                                .withXml(new LegacyMavenPomTransformerAction(alignmentConfiguration,
                                        resolvedDependenciesRepository));
                    }
                });
    }
}
