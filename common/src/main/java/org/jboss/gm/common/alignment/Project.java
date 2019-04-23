package org.jboss.gm.common.alignment;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Project extends Module {

    @JsonProperty
    private String group;

    public Project() {
    }

    public Project(String group, String name) {
        super(name);
        this.group = group;
    }

    public String getGroup() {
        return group;
    }

    // the root project is always the first project in the list
    private List<Module> modules = new ArrayList<>();

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
    }

    public Module findCorrespondingModule(String name) {
        Validate.notEmpty(name, "Supplied project name cannot be empty");
        return modules.stream().filter(m -> name.equals(m.getName())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Project " + name + "does not exist"));
    }

}
