package org.jboss.gm.analyzer.alignment.util;

import java.util.Arrays;
import java.util.List;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;

public class FilteringCustomLogger implements OutputEventListener {

    private final OutputEventListener delegate;

    private final List<String> ignoreCategories = Arrays.asList(
            "org.gradle.internal.execution.steps.SkipUpToDateStep",
            "org.gradle.internal.execution.steps.ResolveCachingStateStep",
            "org.gradle.api.internal.file.collections.DirectoryFileTree",
            "org.gradle.execution.plan.DefaultPlanExecutor",
            "org.gradle.configuration.project.BuildScriptProcessor",
            "org.gradle.execution.TaskNameResolvingBuildConfigurationAction"
            );

    @SuppressWarnings("FieldCanBeLocal")
    private final String defaultCategory = "org.gradle.api.Task";

    public FilteringCustomLogger(OutputEventListener outputEventListener) {
        this.delegate = outputEventListener;
    }

    @Override
    public void onOutput(OutputEvent event) {
        LogEvent logEvent = (LogEvent) event;

        if (!ignoreCategories.contains(logEvent.getCategory())) {

            if ( ! logEvent.getCategory().equals(defaultCategory) )
            {
                System.err.println("### Unknown event using category " + logEvent.getCategory());
            }
            delegate.onOutput(event);
        }
    }
}
