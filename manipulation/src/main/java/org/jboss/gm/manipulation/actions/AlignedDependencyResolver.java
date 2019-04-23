/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation.actions;

import static org.jboss.gm.common.ProjectVersionFactory.withGAV;

import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.jboss.gm.common.alignment.Module;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class AlignedDependencyResolver implements Action<DependencyResolveDetails> {
    private final Module module;

    private static final boolean BYPASS;

    static {
        boolean bypass = false;
        try {
            final String property = System.getenv("BYPASS_ALIGNER");
            bypass = Boolean.parseBoolean(property);
        } catch (Exception e) {
            // ignore
        }
        BYPASS = bypass;
    }

    public AlignedDependencyResolver(Module module) {
        this.module = module;
    }

    @Override
    public void execute(DependencyResolveDetails resolveDetails) {
        if (!BYPASS) {
            final ModuleVersionSelector requested = resolveDetails.getRequested();
            final ProjectVersionRef requestedGAV = withGAV(requested.getGroup(), requested.getName(), requested.getVersion());

            final Map<String, ProjectVersionRef> alignedDependencies = module.getAlignedDependencies();
            final String key = requestedGAV.toString();
            final ProjectVersionRef aligned = alignedDependencies.get(key);
            if (aligned != null) {
                resolveDetails.because(key + " is aligned to " + aligned.toString()).useVersion(aligned.getVersionString());
            }
        }
    }
}
