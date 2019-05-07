package org.jboss.gm.manipulation.actions;

import java.util.HashMap;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;

/**
 * Used in order to record the dependencies that don't have a declared version, but a version
 * that is determined at runtime (by a BOM or the Spring Dependency Management Plugin for example)
 */
public class ResolvedDependenciesRepository {

    private Map<ProjectRef, String> gaToVersion = new HashMap<>();

    public void record(ProjectRef projectRef, String version) {
        gaToVersion.put(projectRef, version);
    }

    public String get(ProjectRef projectRef) {
        return gaToVersion.get(projectRef);
    }
}
