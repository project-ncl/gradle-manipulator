package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.jboss.gm.common.alignment.ManipulationModel;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class OverrideDependenciesAction implements Action<Project> {
    private final AlignedDependencyResolver resolver;

    public OverrideDependenciesAction(ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.resolver = new AlignedDependencyResolver(correspondingModule, resolvedDependenciesRepository);
    }

    @Override
    public void execute(Project project) {
        project.getConfigurations().all(configuration -> configuration.getResolutionStrategy().eachDependency(resolver));
    }
}
