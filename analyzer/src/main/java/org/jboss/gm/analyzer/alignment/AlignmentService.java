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

	interface RequestCustomizer {

		Request customize(Request request);

		// Integer.MIN_VALUE is the max order. This means that if we have 2 services for example
		// we with the first one to be invoked before the second, we would give the first one a
		// value for order that is smaller than that of the second one (for example 0 and 10)
		int order();

		RequestCustomizer NOOP = new RequestCustomizer() {
			@Override
			public Request customize(Request request) {
				return request;
			}

			@Override
			public int order() {
				return Integer.MAX_VALUE;
			}
		};

	}

	interface ResponseCustomizer {

		Response customize(Response response);

		// Integer.MIN_VALUE is the max order. This means that if we have 2 services for example
		// we with the first one to be invoked before the second, we would give the first one a
		// value for order that is smaller than that of the second one (for example 0 and 10)
		int order();

		ResponseCustomizer NOOP = new ResponseCustomizer() {
			@Override
			public Response customize(Response response) {
				return response;
			}

			@Override
			public int order() {
				return Integer.MAX_VALUE;
			}
		};

	}
}
