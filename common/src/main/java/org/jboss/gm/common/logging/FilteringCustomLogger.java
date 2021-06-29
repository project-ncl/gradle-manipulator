package org.jboss.gm.common.logging;

import java.util.Arrays;
import java.util.List;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.ILoggerFactory;
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
        OutputEventListenerBackedLoggerContext context = getContext();
        context.setOutputEventListener(new FilteringCustomLogger(context.getOutputEventListener()));
    }

    public static OutputEventListenerBackedLoggerContext getContext() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();

        if (factory instanceof OutputEventListenerBackedLoggerContext) {
            return (OutputEventListenerBackedLoggerContext) factory;
        } else {
            // Don't think this can ever happen...
            throw new ManipulationUncheckedException(
                    "Internal LoggerFactory isn't a OutputEventListenerBackedLoggerContext ({})",
                    factory.getClass());
        }
    }

    private FilteringCustomLogger(OutputEventListener outputEventListener) {
        this.delegate = outputEventListener;
    }

    @Override
    public void onOutput(OutputEvent event) {
        LogEvent logEvent = (LogEvent) event;
        if (ignoreCategories.stream().noneMatch(i -> logEvent.getCategory().startsWith(i))) {
            if (logEvent.getCategory().startsWith("org.commonjava.maven.ext")) {
                delegate.onOutput(new LogEvent(logEvent.getTimestamp(), logEvent.getCategory(), LogLevel.LIFECYCLE,
                        logEvent.getMessage(), logEvent.getThrowable()));
            } else {
                delegate.onOutput(event);
            }
        }
    }
}
