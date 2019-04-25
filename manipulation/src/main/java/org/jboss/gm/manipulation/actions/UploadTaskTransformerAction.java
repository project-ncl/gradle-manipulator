package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.tasks.Upload;
import org.jboss.gm.common.alignment.ManipulationModel;

/**
 * Fixes pom.xml generation in old "maven" plugin.
 * <p>
 * Adds PomTransformer to all MavenResolver repositories in Upload tasks.
 */
public class UploadTaskTransformerAction implements Action<Project> {

    private ManipulationModel alignmentConfiguration;

    public UploadTaskTransformerAction(ManipulationModel alignmentConfiguration) {
        this.alignmentConfiguration = alignmentConfiguration;
    }

    @Override
    public void execute(Project project) {
        if (!project.getPluginManager().hasPlugin("maven")) {
            return;
        }

        project.getTasks().withType(Upload.class).all(upload -> upload.getRepositories()
                .withType(MavenResolver.class).all(resolver -> {
                    resolver.getPom().withXml(new PomTransformer(alignmentConfiguration));
                }));
    }
}
