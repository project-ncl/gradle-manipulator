package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.manipulation.ResolvedDependenciesRepository;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class OverrideDependenciesAction implements Action<Project> {

    private final Logger logger = GMLogger.getLogger(getClass());

    private final AlignedDependencyResolverAction resolver;

    public OverrideDependenciesAction(ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.resolver = new AlignedDependencyResolverAction(correspondingModule, resolvedDependenciesRepository);
    }

    @Override
    public void execute(Project project) {

        project.getConfigurations().all(configuration -> {
            if (configuration.getState() != Configuration.State.UNRESOLVED) {
                // TODO: Can we use reflection to force the state back to unresolved?
                logger.warn("Configuration {} for {} is not in unresolved state", configuration.getName(), project);
            } else {
                configuration.getResolutionStrategy().eachDependency(resolver);
            }
        });
    }
}
