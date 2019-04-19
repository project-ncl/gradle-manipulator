/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation;

import static org.jboss.gm.common.alignment.AlignmentUtils.getCurrentAlignmentModel;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.alignment.AlignmentModel;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.ProjectChangeVersionAction;
import org.jboss.gm.manipulation.actions.PublicationPomTransformerAction;
import org.jboss.gm.manipulation.actions.PublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class ManipulationTask extends DefaultTask {

    public static final String NAME = "useAlignedDependencies";

    @TaskAction
    public void perform() {
        final Project project = getProject();
        final String projectName = project.getName();
        System.out.println("Starting dependencies manipulation task for project " + projectName);

        // get the previously performed alignment
        final AlignmentModel alignmentModel = getCurrentAlignmentModel(project);
        final AlignmentModel.Module correspondingModule = alignmentModel.findCorrespondingModule(projectName);

        // add actions to manipulate project
        project.afterEvaluate(new ProjectChangeVersionAction(correspondingModule));
        project.afterEvaluate(new OverrideDependenciesAction(correspondingModule));
        project.afterEvaluate(new PublishingRepositoryAction());
        project.afterEvaluate(new ManifestUpdateAction(correspondingModule));
        project.afterEvaluate(new UploadTaskTransformerAction(correspondingModule));
        project.afterEvaluate(new PublicationPomTransformerAction(correspondingModule));
    }
}
