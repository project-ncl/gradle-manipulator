package org.jboss.gm.common.model;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains the information extracted from a gradle project and its sub-project required to perform model and version
 * change
 * 
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class ManipulationModel {
    @JsonProperty
    protected String group;

    /**
     * Name of the project as defined by the build system.
     */
    @JsonProperty
    private String name;

    /**
     * Version of the project.
     */
    @JsonProperty
    private String version;

    /**
     * Computed aligned dependencies for this project, indexed by the previous GAV of the dependency, e.g.
     * {@code org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1} could be the key for a dependency aligned with
     * {@code 3.6.3.SP1-redhat-00001} version
     */
    @JsonProperty
    private Map<String, ProjectVersionRef> alignedDependencies = new HashMap<>();

    /**
     * Representation of this project children projects if any, keyed by name.
     */
    @JsonProperty
    private Map<String, ManipulationModel> children = new HashMap<>(7);

    /**
     * Required for Jackson
     */
    public ManipulationModel() {
    }

    public ManipulationModel(String name, String group) {
        this.name = name;
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, ProjectVersionRef> getAlignedDependencies() {
        return alignedDependencies;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String newProjectVersion) {
        this.version = newProjectVersion;
    }

    public String getGroup() {
        return group;
    }

    public Map<String, ManipulationModel> getChildren() {
        return children;
    }

    public void addChild(ManipulationModel child) {
        children.put(child.getName(), child);
    }

    public ManipulationModel findCorrespondingChild(String name) {
        if (StringUtils.isBlank(name)) {
            throw new ManipulationUncheckedException("Supplied child name cannot be empty");
        }
        final ManipulationModel module;
        if (!name.contains(":")) {
            // we provided a simple name so assume we're looking for a direct child
            if (getName().equals(name) || name.isEmpty()) {
                return this;
            }

            module = children.get(name);
            if (module == null) {
                throw new ManipulationUncheckedException("ManipulationModel " + name + " does not exist");
            }
        } else {
            // we provided a project path, so recursively find the corresponding child by removing the initial ":"
            if (name.equals(":")) {
                return this;
            }

            final int index = name.indexOf(':', 1);
            if (index < 0) {
                // we don't have other path separators so remove the leading : and get with name
                return findCorrespondingChild(name.substring(1));
            } else {
                // extract the child name which is the first component of the path and call recursively on it using remaining
                // path
                String childName = name.substring(1, index);
                return findCorrespondingChild(childName).findCorrespondingChild(name.substring(index));
            }
        }

        return module;
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
}
