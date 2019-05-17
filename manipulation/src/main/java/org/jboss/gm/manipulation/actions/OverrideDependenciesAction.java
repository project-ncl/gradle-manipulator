package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class OverrideDependenciesAction implements Action<Project> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AlignedDependencyResolver resolver;

    public OverrideDependenciesAction(ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.resolver = new AlignedDependencyResolver(correspondingModule, resolvedDependenciesRepository);
    }

    @Override
    public void execute(Project project) {

        project.getConfigurations().all(configuration -> {
            if (configuration.getState() != Configuration.State.UNRESOLVED) {
                // TODO: Can we use reflection to force the state back to unresolved?
                logger.warn("Configuration {} for {} is not in unresolved state", configuration.getName(), project);
            }
            else {
                configuration.getResolutionStrategy().eachDependency(resolver);
            }
        });
    }
}
