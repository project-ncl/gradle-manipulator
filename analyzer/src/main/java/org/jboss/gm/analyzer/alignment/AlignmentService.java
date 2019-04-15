package org.jboss.gm.analyzer.alignment;

import java.util.List;

public interface AlignmentService {

	Response align(Request request);

	class Request {
		private final List<? extends GAV> dependencies;
		private final GAV project;

		public Request(GAV project, List<? extends GAV> dependencies) {
			this.dependencies = dependencies;
			this.project = project;
		}

		List<? extends GAV> getDependencies() {
			return dependencies;
		}

		GAV getProject() {
			return project;
		}
	}

	interface Response {

		String getNewProjectVersion();

		String getAlignedVersionOfGav(GAV gav);
	}
}
