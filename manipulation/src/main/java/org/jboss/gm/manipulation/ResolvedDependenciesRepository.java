package org.jboss.gm.manipulation;

import java.util.HashMap;
import java.util.Map;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;

/**
 * Used in order to record the dependencies that don't have a declared version, but a version
 * that is determined at runtime (by a BOM or the Spring Dependency Management Plugin for example)
 */
public class ResolvedDependenciesRepository {

    private final Map<ProjectRef, String> gaToVersion = new HashMap<>();

    private final Logger logger = GMLogger.getLogger(getClass());

    /**
     * Records a dependency which does not have a declared version.
     *
     * @param projectRef the dependency
     * @param version the version
     */
    public void record(ProjectRef projectRef, String version) {
        logger.debug("Recording resolved dependency {}", projectRef);
        gaToVersion.put(projectRef, version);
    }

    /**
     * Gets the version of the given dependency.
     *
     * @param projectRef the dependency
     * @return the version
     */
    public String get(ProjectRef projectRef) {
        return gaToVersion.get(projectRef);
    }
}
