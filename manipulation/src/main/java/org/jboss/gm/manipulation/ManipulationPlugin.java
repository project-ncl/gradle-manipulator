package org.jboss.gm.manipulation;

import static org.jboss.gm.common.alignment.AlignmentUtils.getCurrentAlignmentModel;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jboss.gm.common.alignment.AlignmentModel;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.ProjectChangeVersionAction;
import org.jboss.gm.manipulation.actions.PublicationPomTransformerAction;
import org.jboss.gm.manipulation.actions.PublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;

public class ManipulationPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // apply the maven publishing plugin
        project.getPluginManager().apply(MavenPublishPlugin.class);

        // get the previously performed alignment
        final AlignmentModel alignmentModel = getCurrentAlignmentModel(project);
        final AlignmentModel.Module correspondingModule = alignmentModel.findCorrespondingModule(project.getName());

        // add actions to manipulate project
        project.afterEvaluate(new ProjectChangeVersionAction(correspondingModule));
        project.afterEvaluate(new OverrideDependenciesAction(correspondingModule));
        project.afterEvaluate(new PublishingRepositoryAction());
        project.afterEvaluate(new ManifestUpdateAction(correspondingModule));
        project.afterEvaluate(new UploadTaskTransformerAction(correspondingModule));
        project.afterEvaluate(new PublicationPomTransformerAction(correspondingModule));
    }
}
