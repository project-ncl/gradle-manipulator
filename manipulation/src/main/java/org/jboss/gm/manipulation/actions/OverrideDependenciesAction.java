/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.jboss.gm.common.alignment.Project;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class OverrideDependenciesAction implements Action<org.gradle.api.Project> {
    private final AlignedDependencyResolver resolver;

    public OverrideDependenciesAction(Project.Module correspondingModule) {
        this.resolver = new AlignedDependencyResolver(correspondingModule);
    }

    @Override
    public void execute(org.gradle.api.Project project) {
        project.getConfigurations().all(configuration -> configuration.getResolutionStrategy().eachDependency(resolver));
    }
}
