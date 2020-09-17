package org.jboss.gm.common.rules;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.sink.OutputEventRenderer;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * JUnit Rule to allow info/debug logging to be seen in tests. Works around https://github.com/gradle/gradle/issues/2030
 */
public class LoggingRule implements TestRule {

    private final LogLevel level;

    public LoggingRule(LogLevel level) {
        this.level = level;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {

                ILoggerFactory factory = LoggerFactory.getILoggerFactory();

                if (factory instanceof OutputEventListenerBackedLoggerContext) {
                    OutputEventListenerBackedLoggerContext context = (OutputEventListenerBackedLoggerContext)factory;
                    try {
                        context.setLevel(level);
                        if (context.getOutputEventListener() instanceof OutputEventRenderer) {
                            // Ensure we attach System out/err so any SystemOutRule work.
                            ((OutputEventRenderer) context.getOutputEventListener()).attachSystemOutAndErr();
                            ((OutputEventRenderer) context.getOutputEventListener()).configure(level);
                        }
                        base.evaluate();
                    } finally {
                        // Could also call setLevel in reverse but reset is probably more comprehensive
                        context.reset();
                    }
                }
            }
        };
    }
}
