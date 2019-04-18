package org.jboss.gm.common.alignment;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AlignmentModel {

    @JsonProperty
    private String group;
    @JsonProperty
    private String name;

    public AlignmentModel() {
    }

    public AlignmentModel(String group, String name) {
        this.group = group;
        this.name = name;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
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
        return modules.stream().filter(m -> name.equals(m.name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Project " + name + "does not exist"));
    }

    public static class Module {
        private String name;
        private String newVersion;
        private List<ProjectVersionRef> alignedDependencies = new ArrayList<>();

        public Module() {
        }

        public Module(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ProjectVersionRef> getAlignedDependencies() {
            return alignedDependencies;
        }

        public void setAlignedDependencies(List<ProjectVersionRef> alignedDependencies) {
            this.alignedDependencies = alignedDependencies;
        }

        public String getNewVersion() {
            return newVersion;
        }

        public void setNewVersion(String newVersion) {
            this.newVersion = newVersion;
        }
    }

}
