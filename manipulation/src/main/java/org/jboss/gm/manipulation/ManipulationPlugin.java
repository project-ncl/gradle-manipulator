package org.jboss.gm.manipulation;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jboss.gm.common.alignment.AlignmentModel;
import org.jboss.gm.common.alignment.AlignmentUtils;

public class ManipulationPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // we need to create an empty alignment file at the project root
        // this file will then be populated by the alignment task of each project
        if (project.getRootProject() == project) {
            final AlignmentModel model = AlignmentUtils.getCurrentAlignmentModel(project);
        }
    }
}
