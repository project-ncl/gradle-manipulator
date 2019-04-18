/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.common;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public final class ProjectVersionFactory {
    private ProjectVersionFactory() {
    }

    public static ProjectVersionRef withNewVersion(ProjectVersionRef dependency, String newVersion) {
        return withGAV(dependency.getGroupId(), dependency.getArtifactId(), newVersion);
    }

    public static ProjectVersionRef withGAV(String group, String artifact, String version) {
        return new SimpleProjectVersionRef(group, artifact, version);
    }

    public static ProjectVersionRef withGAVAndConfiguration(String group, String artifact, String version,
            String configuration) {
        // todo: add configuration support?
        return withGAV(group, artifact, version);
    }
}
