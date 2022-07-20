package org.jboss.gm.manipulation.actions;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.manipulation.ResolvedDependenciesRepository;

import static org.jboss.gm.common.versioning.ProjectVersionFactory.withGAV;

/**
 * An action which overrides dependencies.
 *
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class OverrideDependenciesAction implements Action<Project> {

    private final Logger logger = GMLogger.getLogger(getClass());

    private final AlignedDependencyResolverAction resolver;

    private final ManipulationModel module;

    /**
     * Creates a new override dependencies action with the given corresponding module and resolved dependencies
     * repository.
     *
     * @param correspondingModule the corresponding module
     * @param resolvedDependenciesRepository the resolved dependencies repository
     */
    public OverrideDependenciesAction(ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.module = correspondingModule;
        this.resolver = new AlignedDependencyResolverAction(correspondingModule, resolvedDependenciesRepository);
    }

    /**
     * Executes the override dependencies action on the given project
     *
     * @param project the project
     */
    @Override
    public void execute(Project project) {
        project.getConfigurations().all(configuration -> {
            if (configuration.getState() != Configuration.State.UNRESOLVED) {
                // TODO: Can we use reflection to force the state back to unresolved?
                logger.warn("Configuration {} for {} is not in unresolved state", configuration.getName(), project);
            } else {
                logger.debug("Adding GME resolver to configuration {}", configuration.getName());
                configuration.getResolutionStrategy().eachDependency(resolver);

                final Set<ModuleVersionSelector> forcedOriginal = configuration.getResolutionStrategy().getForcedModules();
                final Set<ModuleVersionSelector> forced = new HashSet<>();
                final Map<String, ProjectVersionRef> alignedDependencies = module.getAlignedDependencies();

                if (!forcedOriginal.isEmpty()) {
                    logger.debug("Found forced modules of {}", forcedOriginal);
                    for (ModuleVersionSelector m : forcedOriginal) {
                        final ProjectVersionRef requestedGAV = withGAV(m.getGroup(), m.getName(), m.getVersion());
                        final ProjectVersionRef aligned = alignedDependencies.get(requestedGAV.toString());
                        if (aligned != null) {
                            logger.info("Replacing force override of {} with {} ", requestedGAV, aligned);
                            forced.add(new DefaultModuleVersionSelector(m.getGroup(), m.getName(),
                                    aligned.getVersionString()));
                        } else {
                            forced.add(m);
                        }
                    }
                    logger.debug("Forced resolution strategy is now {} ", forced);
                    configuration.getResolutionStrategy().setForcedModules(forced.toArray());
                }
            }
        });
    }
}
