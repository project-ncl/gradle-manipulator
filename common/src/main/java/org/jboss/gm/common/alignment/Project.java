package org.jboss.gm.common.alignment;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Project {

    @JsonProperty
    private String group;
    @JsonProperty
    private String name;

    public Project() {
    }

    public Project(String group, String name) {
        this.group = group;
        this.name = name;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    @JsonIgnore
    public String getVersion() {
        return modules.get(0).getNewVersion();
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
