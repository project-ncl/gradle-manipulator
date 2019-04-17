package org.jboss.gm.analyzer.alignment;

import java.util.HashMap;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

public class DummyAlignmentService implements AlignmentService {

	@Override
	public Response align(Request request) {
		return new DummyResponse(getProjectAlignment(request), getAlignedDependencies(request));
	}

	// transforms the version by simply appending "-redhat-00001"
	private String getProjectAlignment(Request request) {
		return request.getProject().getVersionString() + "-redhat-00002";
	}

	// only transforms the first dependency by simply appending "-redhat-00001"
	private Map<String, String> getAlignedDependencies(Request request) {
		final Map<String, String> result = new HashMap<>();
		if (request.getDependencies().isEmpty()) {
			return result;
		}

		final ProjectVersionRef first = request.getDependencies().get(0);
		result.put(first.toString(), first.getVersionString() + "-redhat-00001");
		return result;
	}

	private static class DummyResponse implements Response{
		private final String newProjectVersion;
		private final Map<String, String> dependencyAlignments;

		public DummyResponse(String newProjectVersion, Map<String, String> dependencyAlignments) {
			this.newProjectVersion = newProjectVersion;
			this.dependencyAlignments = dependencyAlignments;
		}

		@Override
		public String getNewProjectVersion() {
			return newProjectVersion;
		}

		@Override
		public String getAlignedVersionOfGav(ProjectVersionRef gav) {
			return dependencyAlignments.get(gav.toString());
		}
	}

}
