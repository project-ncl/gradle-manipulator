package org.jboss.gm.manipulation.actions;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.jboss.gm.common.ProjectVersionFactory.withGAV;

import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.jboss.gm.common.model.ManipulationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class AlignedDependencyResolver implements Action<DependencyResolveDetails> {
    private final ManipulationModel module;
    private final ResolvedDependenciesRepository resolvedDependenciesRepository;

    private static final Logger logger = LoggerFactory.getLogger(AlignedDependencyResolver.class);

    public AlignedDependencyResolver(ManipulationModel module, ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.module = module;
        this.resolvedDependenciesRepository = resolvedDependenciesRepository;
    }

    @Override
    public void execute(DependencyResolveDetails resolveDetails) {
        final ModuleVersionSelector requested = resolveDetails.getRequested();
        String version = requested.getVersion();
        if (isEmpty(requested.getVersion())) {
            if (isEmpty(resolveDetails.getTarget().getVersion())) {
                // this is the case with managed dependencies, where version is provided at resolution time
                logger.warn("Ignoring dependency with empty version {}:{}.", requested.getGroup(), requested.getName());
                return;
            } else {
                version = resolveDetails.getTarget().getVersion();
                if (!isEmpty(version)) {
                    resolvedDependenciesRepository.record(new SimpleProjectRef(requested.getGroup(), requested.getName()),
                            version);
                }
            }
        }

        final ProjectVersionRef requestedGAV = withGAV(requested.getGroup(), requested.getName(), version);

        final Map<String, ProjectVersionRef> alignedDependencies = module.getAlignedDependencies();
        final String key = requestedGAV.toString();
        final ProjectVersionRef aligned = alignedDependencies.get(key);
        if (aligned != null) {
            resolveDetails.because(key + " is aligned to " + aligned.toString()).useVersion(aligned.getVersionString());
        }
    }
}
