package org.jboss.gm.analyzer.alignment;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

public class AlignmentModel {

	private BasicInfo basicInfo;
	// the root project is always the first project in the list
	private List<Module> modules = new ArrayList<>();

	public BasicInfo getBasicInfo() {
		return basicInfo;
	}

	public void setBasicInfo(BasicInfo basicInfo) {
		this.basicInfo = basicInfo;
	}

	public List<Module> getModules() {
		return modules;
	}

	public void setModules(List<Module> modules) {
		this.modules = modules;
	}

	public Module findCorrespondingModule(String name) {
		Validate.notEmpty(name, "Supplied project name cannot be empty");
		return modules.stream().filter(m -> name.equals(m.name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Project " + name + "does not exist"));
	}

	public static class BasicInfo {
		private String group;
		private String name;

		public BasicInfo() {
		}

		public BasicInfo(String group, String name) {
			this.group = group;
			this.name = name;
		}

		public String getGroup() {
			return group;
		}

		public void setGroup(String group) {
			this.group = group;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Module {
		private String name;
		private String newVersion;
		private List<AlignedDependency> alignedDependencies = new ArrayList<>();

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

		public List<AlignedDependency> getAlignedDependencies() {
			return alignedDependencies;
		}

		public void setAlignedDependencies(List<AlignedDependency> alignedDependencies) {
			this.alignedDependencies = alignedDependencies;
		}


		public String getNewVersion() {
			return newVersion;
		}

		public void setNewVersion(String newVersion) {
			this.newVersion = newVersion;
		}
	}

	public static class AlignedDependency implements GAV {
		protected String group;
		protected String name;
		protected String version;
		protected String configuration; //the gradle configuration that produced this dependency

		public AlignedDependency() {
		}

		public AlignedDependency(String group, String name, String version, String configuration) {
			this.group = group;
			this.name = name;
			this.version = version;
			this.configuration = configuration;
		}

		public String getGroup() {
			return group;
		}

		public void setGroup(String group) {
			this.group = group;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		public String getConfiguration() {
			return configuration;
		}

		public void setConfiguration(String configuration) {
			this.configuration = configuration;
		}

		public AlignedDependency withNewVersion(String newVersion) {
			return new AlignedDependency(this.group, this.name, newVersion, this.configuration);
		}
	}
}
