package org.jboss.gm.manipulation;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.ProjectChangeVersionAction;
import org.jboss.gm.manipulation.actions.PublicationPomTransformerAction;
import org.jboss.gm.manipulation.actions.PublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;

import static org.jboss.gm.common.alignment.ManipulationUtils.getCurrentManipulationModel;

public class ManipulationPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // get the previously performed alignment
        final ManipulationModel alignmentModel = getCurrentManipulationModel(project.getRootDir());
        final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(project.getName());

        // add actions to manipulate project
        project.afterEvaluate(new ProjectChangeVersionAction(correspondingModule));
        project.afterEvaluate(new OverrideDependenciesAction(correspondingModule));
        project.afterEvaluate(new PublishingRepositoryAction());
        project.afterEvaluate(new ManifestUpdateAction(correspondingModule));
        project.afterEvaluate(new UploadTaskTransformerAction(correspondingModule));
        project.afterEvaluate(new PublicationPomTransformerAction(correspondingModule));
    }
}
