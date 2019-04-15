package org.jboss.pme.alignment;

import java.beans.Transient;

public interface GAV {

	String getGroup();

	String getName();

	String getVersion();

	@Transient
	default String getIdentifier() {
		return String.format("%s:%s", getGroup(), getName());
	}

	class Simple implements GAV {
		private final String group;
		private final String name;
		private final String version;

		public Simple(String group, String name, String version) {
			this.group = group;
			this.name = name;
			this.version = version;
		}

		@Override
		public String getGroup() {
			return group;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getVersion() {
			return version;
		}
	}
}
