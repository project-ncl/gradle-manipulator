package org.jboss.pnc.gradlemanipulator.common.versioning;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;

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
}
