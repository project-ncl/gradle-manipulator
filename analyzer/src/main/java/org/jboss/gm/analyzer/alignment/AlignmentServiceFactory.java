package org.jboss.gm.analyzer.alignment;

import java.util.Collections;

import org.gradle.api.Project;

public final class AlignmentServiceFactory {

	private AlignmentServiceFactory() {
	}

	public static AlignmentService getAlignmentService(Project project) {
		//TODO: this is going to need a lot of enhancement
		return new WithCustomizersDelegatingAlignmentService(new DummyAlignmentService(),
				Collections.singletonList(AlignmentService.RequestCustomizer.NOOP),
				Collections.singletonList(AlignmentService.ResponseCustomizer.NOOP));
	}
}
