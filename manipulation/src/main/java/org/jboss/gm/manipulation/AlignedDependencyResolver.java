/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation;

import static org.jboss.gm.common.ProjectVersionFactory.withGAV;

import java.util.List;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.jboss.gm.common.alignment.AlignmentModel;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class AlignedDependencyResolver implements Action<DependencyResolveDetails> {
    private final AlignmentModel.Module module;

    public AlignedDependencyResolver(AlignmentModel.Module module) {
        this.module = module;
    }

    @Override
    public void execute(DependencyResolveDetails resolveDetails) {
        final ModuleVersionSelector requested = resolveDetails.getRequested();
        final ProjectVersionRef requestedGAV = withGAV(requested.getGroup(), requested.getName(), requested.getVersion());

        // todo: change aligned dependencies view to be able to query it directly
        final List<ProjectVersionRef> alignedDependencies = module.getAlignedDependencies();
        final int i = alignedDependencies.indexOf(requestedGAV);
        if (i != -1) {
            final ProjectVersionRef aligned = alignedDependencies.get(i);
            final String alignedGAV = aligned.toString();
            resolveDetails.because(requested.toString() + " is aligned to " + alignedGAV).useTarget(alignedGAV);
        }
    }
}
