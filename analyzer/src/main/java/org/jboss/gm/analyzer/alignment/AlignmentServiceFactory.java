package org.jboss.gm.analyzer.alignment;

import java.util.Collections;

public final class AlignmentServiceFactory {

	private AlignmentServiceFactory() {
	}

	public static AlignmentService getAlignmentService() {
		return new WithCustomizersDelegatingAlignmentService(new DummyAlignmentService(),
				Collections.singletonList(AlignmentService.RequestCustomizer.NOOP),
				Collections.singletonList(AlignmentService.ResponseCustomizer.NOOP));
	}
}
