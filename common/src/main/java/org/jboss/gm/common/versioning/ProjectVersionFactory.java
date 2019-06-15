package org.jboss.gm.common.versioning;

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
