package org.jboss.gm.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.utils.ProjectUtils;

/**
 * Contains the information extracted from a gradle project and its sub-project required to perform model and version
 * change
 *
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
@NoArgsConstructor // Required for Jackson
public class ManipulationModel {

    /**
     * Specially handled so that it is both lazy instantiated and not accessed directly in general functions.
     * This allows it to be overridden in utility projects. Use {@link #getLogger()}
     */
    @JsonIgnore
    protected Logger logger;

    @JsonProperty
    @Getter
    @Setter
    protected String group;

    /**
     * Name of the project as defined by the build system.
     *
     * @param name the project name
     * @return the project name
     */
    @JsonProperty
    @Getter
    @Setter
    protected String name;

    /**
     * This should be effectively the same as the folder name.
     *
     * @return the folder name
     */
    @JsonProperty
    @Getter
    protected String projectPathName;

    /**
     * Version of the project.
     *
     * @param version the version of the project
     * @return the version of the project
     */
    @JsonProperty
    @Getter
    @Setter
    protected String version;

    @JsonProperty
    @Getter
    @Setter
    private String originalVersion;

    /**
     * Computed aligned dependencies for this project, indexed by the previous GAV of the dependency, e.g.
     * {@code org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1} could be the key for a dependency aligned with
     * {@code 3.6.3.SP1-redhat-00001} version
     */
    @JsonProperty
    protected Map<String, ProjectVersionRef> alignedDependencies = new TreeMap<>();

    /**
     * Representation of this project children projects if any, keyed by name(artifactId).
     */
    @JsonProperty
    protected Map<String, ManipulationModel> children = new HashMap<>(7);

    public ManipulationModel(Project project) {
        this.name = project.getName();
        this.projectPathName = project.getName();
        this.group = ProjectUtils.getRealGroupId(project);

        /*
         * If a project has been configured like
         * <code>
         * project(':streams') {
         * archivesBaseName = "my-streams"
         * </code>
         * then that applies to any Task that has an archive base type.
         */
        String archiveName = ProjectUtils.getArchivesBaseName(project);
        if (archiveName != null) {
            getLogger().warn("For project {} found archiveName: {}", project.getName(), archiveName);
            this.name = archiveName;
        }

        getLogger().debug("Created manipulation model for project ({}) with path {}", name, projectPathName);
    }

    // Test use only ; avoids having to create a Project to test this class.
    ManipulationModel(String projectPathName, String name, String group) {
        this.name = name;
        this.projectPathName = projectPathName;
        this.group = group;
    }

    @Override
    public String toString() {
        return getGroup() + ':' + getName() + ':' + getVersion();
    }

    /**
     * Returns the alignments for this project only, doesn't include children.
     *
     * @return the alignments for this project only
     */
    public Map<String, ProjectVersionRef> getAlignedDependencies() {
        return alignedDependencies;
    }

    /**
     * Returns all alignments for this project and those of the children.
     *
     * @return all alignments for this project and those of the children
     */
    @JsonIgnore
    public Map<String, ProjectVersionRef> getAllAlignedDependencies() {
        final Map<String, ProjectVersionRef> result = new HashMap<>();
        addDependenciesRec(this, result);
        return result;
    }

    private void addDependenciesRec(ManipulationModel manipulationModel, Map<String, ProjectVersionRef> result) {
        result.putAll(manipulationModel.getAlignedDependencies());
        for (ManipulationModel child : manipulationModel.getChildren().values()) {
            addDependenciesRec(child, result);
        }
    }

    public Map<String, ManipulationModel> getChildren() {
        return children;
    }

    public void addChild(ManipulationModel child) {
        children.put(child.projectPathName, child);
    }

    public ManipulationModel findCorrespondingChild(Project name) {
        return findCorrespondingChild(name.getPath());
    }

    // Only test access.
    protected ManipulationModel findCorrespondingChild(String path) {
        if (StringUtils.isBlank(path)) {
            throw new ManipulationUncheckedException("Supplied child name cannot be empty");
        }

        if (!path.contains(":")) {
            // we provided a simple name so assume we're looking for a direct child
            if (children.containsKey(path) && getName().equals(path)) {
                getLogger().error(
                        "Child module ({}) has matching name to current module ({}) and unable to differentiate",
                        children.keySet(),
                        getName());
            }

            if (getName().equals(path) || path.isEmpty()) {
                return this;
            }

            return getChild(path);
        } else {
            // we provided a project path, so recursively find the corresponding child by removing the initial ":"
            if (path.equals(":")) {
                return this;
            }
            final int index = path.indexOf(':', 1);
            if (index < 0) {
                // we don't have other path separators so remove the leading : and get with name
                return getChild(path.substring(1));
            } else {
                // extract the child name which is the first component of the path and call recursively on it using remaining
                // path
                String childName = path.substring(1, index);
                return getChild(childName).findCorrespondingChild(path.substring(index));
            }
        }
    }

    private ManipulationModel getChild(String path) {
        ManipulationModel result = children.get(path);
        getLogger().debug("Looking for {} in model (with children {}) and found {}", path, children.keySet(), result);
        if (result == null) {
            throw new ManipulationUncheckedException("ManipulationModel '{}' does not exist", path);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ManipulationModel model = (ManipulationModel) o;

        if (!group.equals(model.group))
            return false;
        if (!name.equals(model.name))
            return false;
        return version.equals(model.version);

    }

    @Override
    public int hashCode() {
        int result = group.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    private Logger getLogger() {
        if (logger == null) {
            logger = GMLogger.getLogger(getClass());
        }
        return logger;
    }
}
