package org.jboss.gm.common.alignment;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AlignedProject extends Module {

    @JsonProperty
    private String group;

    @JsonProperty
    private Map<String, Module> modules = new HashMap<>(7);

    public AlignedProject() {
    }

    public AlignedProject(String group, String name) {
        super(name);
        this.group = group;
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
