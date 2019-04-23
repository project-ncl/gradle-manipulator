/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.jboss.gm.common.alignment.AlignmentModel;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class ProjectChangeVersionAction implements Action<Project> {
    private final AlignmentModel.Module module;

    public ProjectChangeVersionAction(AlignmentModel.Module correspondingModule) {
        this.module = correspondingModule;
    }

    @Override
    public void execute(Project project) {
        project.setVersion(module.getNewVersion());
    }
}
