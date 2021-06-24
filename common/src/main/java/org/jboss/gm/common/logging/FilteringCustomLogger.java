package org.jboss.gm.common.logging;

import java.util.Arrays;
import java.util.List;

import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.LoggerFactory;

public class FilteringCustomLogger implements OutputEventListener {

    private final OutputEventListener delegate;

    private final List<String> ignoreCategories = Arrays.asList(
            "org.gradle.api",
            "org.gradle.cache",
            "org.gradle.configuration.project.BuildScriptProcessor",
            "org.gradle.execution",
            "org.gradle.internal",
            "org.gradle.launcher.daemon",
            "org.gradle.workers.internal.WorkerDaemonClientsManager");

    public static void enableFilter() {
        OutputEventListenerBackedLoggerContext context = (OutputEventListenerBackedLoggerContext) LoggerFactory
                .getILoggerFactory();
        context.setOutputEventListener(new FilteringCustomLogger(context.getOutputEventListener()));
    }

    private FilteringCustomLogger(OutputEventListener outputEventListener) {
        this.delegate = outputEventListener;
    }

    @Override
    public void onOutput(OutputEvent event) {
        LogEvent logEvent = (LogEvent) event;
        if (ignoreCategories.stream().noneMatch(i -> logEvent.getCategory().startsWith(i))) {
            delegate.onOutput(event);
        }
    }
}
