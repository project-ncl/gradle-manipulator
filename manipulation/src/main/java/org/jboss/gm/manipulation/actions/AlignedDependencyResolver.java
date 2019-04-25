/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation.actions;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.jboss.gm.common.ProjectVersionFactory.withGAV;

import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class AlignedDependencyResolver implements Action<DependencyResolveDetails> {
    private final ManipulationModel module;

    private static final Logger log = LoggerFactory.getLogger(AlignedDependencyResolver.class);

    public AlignedDependencyResolver(ManipulationModel module) {
        this.module = module;
    }

    @Override
    public void execute(DependencyResolveDetails resolveDetails) {
        final ModuleVersionSelector requested = resolveDetails.getRequested();
        if (isEmpty(requested.getVersion())) {
            // this is the case with managed dependencies, where version is provided at resolution time
            log.warn("Ignoring dependency with empty version {}:{}.", requested.getGroup(), requested.getName());
            return;
        }

        final ProjectVersionRef requestedGAV = withGAV(requested.getGroup(), requested.getName(), requested.getVersion());

        final Map<String, ProjectVersionRef> alignedDependencies = module.getAlignedDependencies();
        final String key = requestedGAV.toString();
        final ProjectVersionRef aligned = alignedDependencies.get(key);
        if (aligned != null) {
            resolveDetails.because(key + " is aligned to " + aligned.toString()).useVersion(aligned.getVersionString());
        }
    }
}
