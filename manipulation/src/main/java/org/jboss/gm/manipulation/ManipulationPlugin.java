package org.jboss.gm.manipulation;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ManipulationPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().create(ManipulationTask.NAME, ManipulationTask.class);
    }
}
