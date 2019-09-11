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
            "org.gradle.api.internal.file.collections.DirectoryFileTree",
            "org.gradle.configuration.project.BuildScriptProcessor",
            "org.gradle.execution.TaskNameResolvingBuildConfigurationAction",
            "org.gradle.execution.plan.DefaultPlanExecutor");

    @SuppressWarnings("FieldCanBeLocal")
    private final String ignoreInternalCategory = "org.gradle.internal";

    @SuppressWarnings("FieldCanBeLocal")
    private final String defaultCategory = "org.gradle.api.Task";

    /**
     * Don't filter our own categories
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final String ownCategory = "org.jboss.gm";

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
        if (!logEvent.getCategory().startsWith(ignoreInternalCategory) && !ignoreCategories.contains(logEvent.getCategory())) {

            if (!logEvent.getCategory().startsWith(ownCategory) && !logEvent.getCategory().startsWith(defaultCategory)) {
                // Can't use logger to output the warning as causes stack overflow.
                // TODO: Remove?
                System.err.println("Unknown event using category " + logEvent.getCategory() + " : ");
                delegate.onOutput(event);
                System.err.println(".... completed unknown event.");
            }
            delegate.onOutput(event);
        }
    }
}
