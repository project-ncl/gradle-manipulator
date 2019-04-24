/**
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.common.alignment;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        Validate.notEmpty(name, "Supplied module name cannot be empty");

        if (getName().equals(name)) {
            return this;
        }

        final ManipulationModel module = children.get(name);
        if (module == null) {
            throw new IllegalArgumentException("ManipulationModel " + name + " does not exist");
        }
        return module;
    }
}
