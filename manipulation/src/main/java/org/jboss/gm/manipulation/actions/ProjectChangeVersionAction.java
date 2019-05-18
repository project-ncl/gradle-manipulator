package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class ProjectChangeVersionAction implements Action<Project> {
    private final ManipulationModel module;

    public ProjectChangeVersionAction(ManipulationModel correspondingModule) {
        this.module = correspondingModule;
    }

    @Override
    public void execute(Project project) {
        project.setVersion(module.getVersion());
    }
}
