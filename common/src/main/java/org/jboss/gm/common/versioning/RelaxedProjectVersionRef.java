package org.jboss.gm.common.versioning;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.TypeAndClassifier;
import org.commonjava.atlas.maven.ident.ref.VersionlessArtifactRef;
import org.commonjava.atlas.maven.ident.version.SingleVersion;
import org.commonjava.atlas.maven.ident.version.VersionSpec;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;

/**
 * This is a special ProjectVersionRef that allows a null for a version and delegates to the
 * appropriate implementation.
 * <p>
 * The majority of this is merely a wrapper and not implemented - just enough to store the
 * original mapping key in the {@link org.jboss.gm.common.ManipulationCache} and provide comparison functionality.
 *
 * @see org.commonjava.atlas.maven.ident.ref.SimpleProjectRef
 * @see org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef
 *
 * @author ncross
 */
public class RelaxedProjectVersionRef
        implements ProjectVersionRef {
    private ProjectVersionRef projectVersionRefDelegate;
    private ProjectRef projectRefDelegate;

    private RelaxedProjectVersionRef(final String groupId, final String artifactId, final String versionString) {
        if (StringUtils.isEmpty(versionString)) {
            projectRefDelegate = new SimpleProjectRef(groupId, artifactId);
        } else {
            projectVersionRefDelegate = new SimpleProjectVersionRef(groupId, artifactId, versionString);
        }
    }

    public RelaxedProjectVersionRef(ResolvedDependency dependency) {
        this(dependency.getModuleGroup(), dependency.getModuleName(), dependency.getModuleVersion());
    }

    public RelaxedProjectVersionRef(Dependency dependency) {
        this(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

    public RelaxedProjectVersionRef(ProjectVersionRef projectVersionRefDelegate) {
        this.projectVersionRefDelegate = projectVersionRefDelegate;
    }

    @Override
    public ProjectVersionRef asProjectVersionRef() {
        return projectVersionRefDelegate;
    }

    @Override
    public ArtifactRef asPomArtifact() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ArtifactRef asJarArtifact() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ArtifactRef asArtifactRef(String type, String classifier) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ArtifactRef asArtifactRef(TypeAndClassifier tc) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public VersionSpec getVersionSpecRaw() {
        if (projectVersionRefDelegate != null) {
            return projectVersionRefDelegate.getVersionSpecRaw();
        }
        return null;
    }

    @Override
    public String getVersionStringRaw() {
        if (projectVersionRefDelegate != null) {
            return projectVersionRefDelegate.getVersionStringRaw();
        }
        return null;
    }

    @Override
    public boolean isRelease() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public boolean isSpecificVersion() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public boolean matchesVersion(SingleVersion version) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ProjectVersionRef selectVersion(String version) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ProjectVersionRef selectVersion(String version, boolean force) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ProjectVersionRef selectVersion(SingleVersion version) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ProjectVersionRef selectVersion(SingleVersion version, boolean force) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public ProjectVersionRef newRef(String groupId, String artifactId, SingleVersion version) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public VersionSpec getVersionSpec() {
        if (projectVersionRefDelegate != null) {
            return projectVersionRefDelegate.getVersionSpec();
        }
        return null;
    }

    @Override
    public boolean versionlessEquals(ProjectVersionRef other) {
        if (projectRefDelegate != null) {
            return projectRefDelegate.equals(other.asProjectRef());
        } else {
            return projectVersionRefDelegate.versionlessEquals(other);
        }
    }

    @Override
    public boolean isCompound() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public boolean isSnapshot() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public String getVersionString() {
        if (projectVersionRefDelegate != null) {
            return projectVersionRefDelegate.getVersionString();
        }
        return null;
    }

    @Override
    public boolean isVariableVersion() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public String getGroupId() {
        if (projectRefDelegate != null) {
            return projectRefDelegate.getGroupId();
        } else {
            return projectVersionRefDelegate.getGroupId();
        }
    }

    @Override
    public String getArtifactId() {
        if (projectRefDelegate != null) {
            return projectRefDelegate.getArtifactId();
        } else {
            return projectVersionRefDelegate.getArtifactId();
        }
    }

    @Override
    public ProjectRef asProjectRef() {
        return projectRefDelegate;
    }

    @Override
    public VersionlessArtifactRef asVersionlessPomArtifact() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public VersionlessArtifactRef asVersionlessJarArtifact() {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public VersionlessArtifactRef asVersionlessArtifactRef(String type, String classifier) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public VersionlessArtifactRef asVersionlessArtifactRef(TypeAndClassifier tc) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @Override
    public boolean matches(ProjectRef ref) {
        throw new ManipulationUncheckedException("Not implemented");
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(ProjectRef projectRef) {
        if (projectRefDelegate != null) {
            return projectRefDelegate.compareTo(projectRef);
        } else {
            return projectVersionRefDelegate.compareTo(projectRef);
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (projectRefDelegate != null) {
            return projectRefDelegate.equals(obj);
        } else {
            return projectVersionRefDelegate.equals(obj);
        }
    }

    @Override
    public int hashCode() {
        if (projectRefDelegate != null) {
            return projectRefDelegate.hashCode();
        } else {
            return projectVersionRefDelegate.hashCode();
        }
    }

    @Override
    public String toString() {
        if (projectRefDelegate != null) {
            return projectRefDelegate.toString();
        } else {
            return projectVersionRefDelegate.toString();
        }
    }

}
