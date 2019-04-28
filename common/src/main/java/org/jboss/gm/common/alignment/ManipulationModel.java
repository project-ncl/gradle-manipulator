/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.common.alignment;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.Validate;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains the information extracted from a gradle project and its sub-project required to perform alignment and version
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
        Validate.notEmpty(name, "Supplied child name cannot be empty");

        final ManipulationModel module;
        if (!name.contains(":")) {
            // we provided a simple name so assume we're looking for a direct child
            if (getName().equals(name) || name.isEmpty()) {
                return this;
            }

            module = children.get(name);
            if (module == null) {
                throw new IllegalArgumentException("ManipulationModel " + name + " does not exist");
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
