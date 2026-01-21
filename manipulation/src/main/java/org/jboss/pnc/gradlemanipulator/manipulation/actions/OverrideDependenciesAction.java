package org.jboss.pnc.gradlemanipulator.manipulation.actions;

import static org.jboss.pnc.gradlemanipulator.common.versioning.ProjectVersionFactory.withGAV;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.logging.Logger;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;
import org.jboss.pnc.gradlemanipulator.common.model.ManipulationModel;
import org.jboss.pnc.gradlemanipulator.manipulation.ResolvedDependenciesRepository;

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
    public OverrideDependenciesAction(
            ManipulationModel correspondingModule,
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
        project.getConfigurations().configureEach(configuration -> {
            if (configuration.getState() != Configuration.State.UNRESOLVED) {
                // TODO: Can we use reflection to force the state back to unresolved?
                logger.warn("Configuration {} for {} is not in unresolved state", configuration.getName(), project);
            } else {
                logger.trace(
                        "Adding GME resolver to configuration {} on project {}",
                        configuration.getName(),
                        project.getPath());
                configuration.getResolutionStrategy().eachDependency(resolver);

                final Set<ModuleVersionSelector> forcedOriginal = configuration.getResolutionStrategy()
                        .getForcedModules();
                final Set<ModuleVersionSelector> forced = new HashSet<>();
                final Map<String, ProjectVersionRef> alignedDependencies = module.getAlignedDependencies();

                if (!forcedOriginal.isEmpty()) {
                    logger.debug("Found forced modules of {}", forcedOriginal);
                    for (ModuleVersionSelector m : forcedOriginal) {
                        final ProjectVersionRef requestedGAV = withGAV(m.getGroup(), m.getName(), m.getVersion());
                        final ProjectVersionRef aligned = alignedDependencies.get(requestedGAV.toString());
                        if (aligned != null) {
                            logger.info("Replacing force override of {} with {} ", requestedGAV, aligned);
                            forced.add(
                                    DefaultModuleVersionSelector.newSelector(
                                            DefaultModuleIdentifier.newId(
                                                    m.getGroup(),
                                                    m.getName()),
                                            aligned.getVersionString()));
                        } else {
                            forced.add(m);
                        }
                    }
                    logger.debug("Forced resolution strategy is now {} ", forced);
                    configuration.getResolutionStrategy().setForcedModules(forced.toArray());
                }

                configuration.getDependencies().configureEach(d -> {
                    if (d instanceof org.gradle.api.artifacts.ExternalModuleDependency) {
                        ExternalModuleDependency externalModuleDependency = (ExternalModuleDependency) d;
                        if (StringUtils
                                .isNotEmpty(externalModuleDependency.getVersionConstraint().getStrictVersion())) {
                            logger.debug(
                                    "Found version constraint of {} for {}",
                                    externalModuleDependency.getVersionConstraint(),
                                    d);
                            final ProjectVersionRef requestedGAV = withGAV(
                                    externalModuleDependency.getModule().getGroup(),
                                    externalModuleDependency.getModule().getName(),
                                    externalModuleDependency.getVersionConstraint().getStrictVersion());
                            final ProjectVersionRef aligned = alignedDependencies.get(requestedGAV.toString());
                            if (aligned != null) {
                                logger.info(
                                        "Replacing strictly with forced version for {} with {}",
                                        requestedGAV,
                                        aligned);
                                forced.add(
                                        DefaultModuleVersionSelector.newSelector(
                                                DefaultModuleIdentifier.newId(
                                                        externalModuleDependency.getModule().getGroup(),
                                                        externalModuleDependency.getModule().getName()),
                                                aligned.getVersionString()));
                            }
                            configuration.getResolutionStrategy().setForcedModules(forced.toArray());
                        }
                    }
                });
            }
        });
    }
}
