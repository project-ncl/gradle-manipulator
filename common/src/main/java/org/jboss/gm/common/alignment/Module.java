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
 * Represents any buildable module (as in maven module or gradle project) which dependencies and version can be aligned
 * 
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class Module {
    @JsonProperty
    protected String group;

    /**
     * Name of the module as defined by the build system.
     */
    @JsonProperty
    private String name;

    /**
     * Version of the module.
     */
    @JsonProperty
    private String version;

    /**
     * Computed aligned dependencies for this module, indexed by the previous GAV of the dependency, e.g.
     * {@code org.jboss.resteasy:resteasy-jaxrs:3.6.3.SP1} could be the key for a dependency aligned with
     * {@code 3.6.3.SP1-redhat-00001} version
     */
    @JsonProperty
    private Map<String, ProjectVersionRef> alignedDependencies = new HashMap<>();

    /**
     * Children modules if any.
     */
    @JsonProperty
    private Map<String, Module> modules = new HashMap<>(7);

    /**
     * Required for Jackson
     */
    public Module() {
    }

    public Module(String name, String group) {
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

    public Map<String, Module> getModules() {
        return modules;
    }

    public void addModule(Module module) {
        modules.put(module.getName(), module);
    }

    public Module findCorrespondingModule(String name) {
        Validate.notEmpty(name, "Supplied module name cannot be empty");

        if (getName().equals(name)) {
            return this;
        }

        final Module module = modules.get(name);
        if (module == null) {
            throw new IllegalArgumentException("Module " + name + " does not exist");
        }
        return module;
    }
}
