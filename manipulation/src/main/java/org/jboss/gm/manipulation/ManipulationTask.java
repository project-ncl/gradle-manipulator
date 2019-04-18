/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation;

import static org.jboss.gm.common.alignment.AlignmentUtils.getCurrentAlignmentModel;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.tasks.TaskAction;
import org.jboss.gm.common.alignment.AlignmentModel;

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

        // update project dependencies using custom resolution
        updateProjectDependencies(project, new AlignedDependencyResolver(correspondingModule));
    }

    private void updateProjectDependencies(Project project, final Action<DependencyResolveDetails> resolver) {
        project.getConfigurations().all(configuration -> configuration.getResolutionStrategy().eachDependency(resolver));
    }
}
