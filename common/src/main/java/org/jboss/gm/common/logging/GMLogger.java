package org.jboss.gm.common.logging;

import java.util.Arrays;
import org.aeonbits.owner.ConfigCache;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jboss.gm.common.Configuration;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;
import org.slf4j.Marker;

@SuppressWarnings("unused")
public class GMLogger implements Logger {

    private static final String ANSI_BOLD_WHITE = "\u001B[0;1m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BRIGHT_YELLOW = "\u001B[93m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_DARK_GRAY = "\u001B[90m";

    private static final Configuration configuration;

    static {
        configuration = ConfigCache.getOrCreate(Configuration.class);
        Logger logger = Logging.getLogger(GMLogger.class);

        if (logger.isWarnEnabled()) {
            logger.warn(
                    "{}This plugin overrides default logging output for info and debug.\n\tIt sends info output to Lifecycle category\n\tIt sends debug output to info category.{}",
                    configuration.addLoggingColours() ? ANSI_BRIGHT_YELLOW : "",
                    configuration.addLoggingColours() ? ANSI_RESET : "");
        }
    }

    private final Logger delegate;

    public static Logger getLogger(Class<?> c) {
        return new GMLogger(c.getName());
    }

    private GMLogger(String name) {
        delegate = Logging.getLogger(name);
    }

    // TODO: Change this to a formatting method that allows full stacktrace formatting, MDC etc.
    private String injectLoggerFormatting(String msg) {

        StringBuilder sb = new StringBuilder();

        // Using a SecurityManager would be faster but we want the line number. And the StackWalker API
        // is JDK9 and above.
        StackTraceElement[] result = Thread.currentThread().getStackTrace();
        // 0 is this getStackTrace call.
        // 1 is this method
        // 2 is the logging method
        // 3 is the target we want.
        // And therefore we need to ensure we have at least 3 frames to examine.
        if (result.length < 4) {
            throw new ManipulationUncheckedException(
                    "Internal logging failure ; not enough stacktrace elements {}",
                    Arrays.toString(result));
        }
        String loggingLevel = result[2].getMethodName().toUpperCase();

        if (configuration.addLoggingClassnameLinenumber()) {

            if (configuration.addLoggingColours()) {
                sb.append(ANSI_DARK_GRAY);
            }
            if (configuration.addLoggingLevel()) {
                sb.append('[');
                sb.append(loggingLevel);
                sb.append(']');
            }
            sb.append('[');
            sb.append(result[3].getFileName());
            sb.append(':');
            sb.append(result[3].getLineNumber());
            sb.append("] ");
            if (configuration.addLoggingColours()) {
                sb.append(ANSI_RESET);
            }
        }
        // Currently only colouring warning or error messages for ease of spotting.
        if (configuration.addLoggingColours()) {
            switch (loggingLevel) {
                case "WARN":
                    sb.append(ANSI_PURPLE);
                    break;
                case "ERROR":
                    sb.append(ANSI_RED);
                    break;
            }
        }
        sb.append(msg);
        if (configuration.addLoggingColours() && (loggingLevel.equals("WARN") || loggingLevel.equals("ERROR"))) {
            sb.append(ANSI_RESET);
        }

        return sb.toString();
    }

    /**
     * Returns true if lifecycle log level is enabled for this logger.
     */
    @Override
    public boolean isLifecycleEnabled() {
        return delegate.isLifecycleEnabled();
    }

    /**
     * Multiple-parameters friendly debug method
     *
     * @param message the log message
     * @param objects the log message parameters
     */
    @Override
    public void debug(String message, Object... objects) {
        delegate.info(injectLoggerFormatting(message), objects);
    }

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     */
    @Override
    public void lifecycle(String message) {
        delegate.lifecycle(message);
    }

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    @Override
    public void lifecycle(String message, Object... objects) {
        delegate.lifecycle(injectLoggerFormatting(message), objects);
    }

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     * @param throwable the exception to log.
     */
    @Override
    public void lifecycle(String message, Throwable throwable) {
        delegate.lifecycle(injectLoggerFormatting(message), throwable);
    }

    /**
     * Returns true if quiet log level is enabled for this logger.
     */
    @Override
    public boolean isQuietEnabled() {
        return delegate.isQuietEnabled();
    }

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     */
    @Override
    public void quiet(String message) {
        delegate.quiet(message);
    }

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    @Override
    public void quiet(String message, Object... objects) {
        delegate.quiet(injectLoggerFormatting(message), objects);
    }

    /**
     * Logs the given message at info log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    @Override
    public void info(String message, Object... objects) {
        delegate.lifecycle(injectLoggerFormatting(message), objects);
    }

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     * @param throwable the exception to log.
     */
    @Override
    public void quiet(String message, Throwable throwable) {
        delegate.quiet(injectLoggerFormatting(message), throwable);
    }

    /**
     * Returns true if the given log level is enabled for this logger.
     *
     * @param level the log level.
     */
    @Override
    public boolean isEnabled(LogLevel level) {
        return delegate.isEnabled(level);
    }

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     */
    @Override
    public void log(LogLevel level, String message) {
        delegate.log(level, message);
    }

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     * @param objects the log message parameters.
     */
    @Override
    public void log(LogLevel level, String message, Object... objects) {
        delegate.log(level, message, objects);
    }

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     * @param throwable the exception to log.
     */
    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        delegate.log(level, message, throwable);
    }

    /**
     * Return the name of this <code>Logger</code> instance.
     *
     * @return name of this logger instance
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    /**
     * Is the logger instance enabled for the TRACE level?
     *
     * @return True if this Logger is enabled for the TRACE level,
     *         false otherwise.
     * @since 1.4
     */
    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    /**
     * Log a message at the TRACE level.
     *
     * @param msg the message string to be logged
     * @since 1.4
     */
    @Override
    public void trace(String msg) {
        delegate.debug(injectLoggerFormatting(msg));
    }

    /**
     * Log a message at the TRACE level according to the specified format
     * and argument.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the TRACE level.
     *
     * @param format the format string
     * @param arg the argument
     * @since 1.4
     */
    @Override
    public void trace(String format, Object arg) {
        delegate.debug(injectLoggerFormatting(format), arg);
    }

    /**
     * Log a message at the TRACE level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the TRACE level.
     *
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     * @since 1.4
     */
    @Override
    public void trace(String format, Object arg1, Object arg2) {
        delegate.debug(injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * Log a message at the TRACE level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the TRACE level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for TRACE. The variants taking {@link #trace(String, Object) one} and
     * {@link #trace(String, Object, Object) two} arguments exist solely in order to avoid this hidden cost.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     * @since 1.4
     */
    @Override
    public void trace(String format, Object... arguments) {
        delegate.debug(injectLoggerFormatting(format), arguments);
    }

    /**
     * Log an exception (throwable) at the TRACE level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     * @since 1.4
     */
    @Override
    public void trace(String msg, Throwable t) {
        delegate.debug(injectLoggerFormatting(msg), t);
    }

    /**
     * Similar to {@link #isTraceEnabled()} method except that the
     * marker data is also taken into account.
     *
     * @param marker The marker data to take into consideration
     * @return True if this Logger is enabled for the TRACE level,
     *         false otherwise.
     *
     * @since 1.4
     */
    @Override
    public boolean isTraceEnabled(Marker marker) {
        return delegate.isTraceEnabled(marker);
    }

    /**
     * Log a message with the specific Marker at the TRACE level.
     *
     * @param marker the marker data specific to this log statement
     * @param msg the message string to be logged
     * @since 1.4
     */
    @Override
    public void trace(Marker marker, String msg) {
        delegate.debug(marker, msg);
    }

    /**
     * This method is similar to {@link #trace(String, Object)} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg the argument
     * @since 1.4
     */
    @Override
    public void trace(Marker marker, String format, Object arg) {
        delegate.debug(marker, injectLoggerFormatting(format), arg);
    }

    /**
     * This method is similar to {@link #trace(String, Object, Object)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     * @since 1.4
     */
    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        delegate.debug(marker, injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * This method is similar to {@link #trace(String, Object...)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param argArray an array of arguments
     * @since 1.4
     */
    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        delegate.debug(marker, injectLoggerFormatting(format), argArray);
    }

    /**
     * This method is similar to {@link #trace(String, Throwable)} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     * @since 1.4
     */
    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        delegate.debug(marker, injectLoggerFormatting(msg), t);
    }

    /**
     * Is the logger instance enabled for the DEBUG level?
     *
     * @return True if this Logger is enabled for the DEBUG level,
     *         false otherwise.
     */
    @Override
    public boolean isDebugEnabled() {
        return delegate.isInfoEnabled();
    }

    /**
     * Log a message at the DEBUG level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void debug(String msg) {
        delegate.info(injectLoggerFormatting(msg));
    }

    /**
     * Log a message at the DEBUG level according to the specified format
     * and argument.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the DEBUG level.
     *
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void debug(String format, Object arg) {
        delegate.info(injectLoggerFormatting(format), arg);
    }

    /**
     * Log a message at the DEBUG level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the DEBUG level.
     *
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void debug(String format, Object arg1, Object arg2) {
        delegate.info(injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * Log an exception (throwable) at the DEBUG level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void debug(String msg, Throwable t) {
        delegate.info(injectLoggerFormatting(msg), t);
    }

    /**
     * Similar to {@link #isDebugEnabled()} method except that the
     * marker data is also taken into account.
     *
     * @param marker The marker data to take into consideration
     * @return True if this Logger is enabled for the DEBUG level,
     *         false otherwise.
     */
    @Override
    public boolean isDebugEnabled(Marker marker) {
        return delegate.isInfoEnabled(marker);
    }

    /**
     * Log a message with the specific Marker at the DEBUG level.
     *
     * @param marker the marker data specific to this log statement
     * @param msg the message string to be logged
     */
    @Override
    public void debug(Marker marker, String msg) {
        delegate.info(marker, injectLoggerFormatting(msg));
    }

    /**
     * This method is similar to {@link #debug(String, Object)} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void debug(Marker marker, String format, Object arg) {
        delegate.info(marker, injectLoggerFormatting(format), arg);
    }

    /**
     * This method is similar to {@link #debug(String, Object, Object)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        delegate.info(marker, injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * This method is similar to {@link #debug(String, Object...)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        delegate.info(marker, injectLoggerFormatting(format), arguments);
    }

    /**
     * This method is similar to {@link #debug(String, Throwable)} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        delegate.info(marker, injectLoggerFormatting(msg), t);
    }

    /**
     * Is the logger instance enabled for the INFO level?
     *
     * @return True if this Logger is enabled for the INFO level,
     *         false otherwise.
     */
    @Override
    public boolean isInfoEnabled() {
        return delegate.isLifecycleEnabled();
    }

    /**
     * Log a message at the INFO level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void info(String msg) {
        delegate.lifecycle(injectLoggerFormatting(msg));
    }

    /**
     * Log a message at the INFO level according to the specified format
     * and argument.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the INFO level.
     *
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void info(String format, Object arg) {
        delegate.lifecycle(injectLoggerFormatting(format), arg);
    }

    /**
     * Log a message at the INFO level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the INFO level.
     *
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void info(String format, Object arg1, Object arg2) {
        delegate.lifecycle(injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * Log an exception (throwable) at the INFO level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void info(String msg, Throwable t) {
        delegate.lifecycle(injectLoggerFormatting(msg), t);
    }

    /**
     * Similar to {@link #isInfoEnabled()} method except that the marker
     * data is also taken into consideration.
     *
     * @param marker The marker data to take into consideration
     * @return true if this logger is warn enabled, false otherwise
     */
    @Override
    public boolean isInfoEnabled(Marker marker) {
        return delegate.isLifecycleEnabled();
    }

    /**
     * Log a message with the specific Marker at the INFO level.
     *
     * @param marker The marker specific to this log statement
     * @param msg the message string to be logged
     */
    @Override
    public void info(Marker marker, String msg) {
        delegate.lifecycle(injectLoggerFormatting(msg));
    }

    /**
     * This method is similar to {@link #info(String, Object)} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void info(Marker marker, String format, Object arg) {
        delegate.lifecycle(injectLoggerFormatting(format), arg);
    }

    /**
     * This method is similar to {@link #info(String, Object, Object)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        delegate.lifecycle(injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * This method is similar to {@link #info(String, Object...)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void info(Marker marker, String format, Object... arguments) {
        delegate.lifecycle(injectLoggerFormatting(format), arguments);
    }

    /**
     * This method is similar to {@link #info(String, Throwable)} method
     * except that the marker data is also taken into consideration.
     *
     * @param marker the marker data for this log statement
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void info(Marker marker, String msg, Throwable t) {
        delegate.lifecycle(injectLoggerFormatting(msg), t);
    }

    /**
     * Is the logger instance enabled for the WARN level?
     *
     * @return True if this Logger is enabled for the WARN level,
     *         false otherwise.
     */
    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    /**
     * Log a message at the WARN level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void warn(String msg) {
        delegate.warn(injectLoggerFormatting(msg));
    }

    /**
     * Log a message at the WARN level according to the specified format
     * and argument.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the WARN level.
     *
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void warn(String format, Object arg) {
        delegate.warn(injectLoggerFormatting(format), arg);
    }

    /**
     * Log a message at the WARN level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the WARN level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for WARN. The variants taking
     * {@link #warn(String, Object) one} and {@link #warn(String, Object, Object) two}
     * arguments exist solely in order to avoid this hidden cost.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void warn(String format, Object... arguments) {
        delegate.warn(injectLoggerFormatting(format), arguments);
    }

    /**
     * Log a message at the WARN level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the WARN level.
     *
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void warn(String format, Object arg1, Object arg2) {
        delegate.warn(injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * Log an exception (throwable) at the WARN level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void warn(String msg, Throwable t) {
        delegate.warn(injectLoggerFormatting(msg), t);
    }

    /**
     * Similar to {@link #isWarnEnabled()} method except that the marker
     * data is also taken into consideration.
     *
     * @param marker The marker data to take into consideration
     * @return True if this Logger is enabled for the WARN level,
     *         false otherwise.
     */
    @Override
    public boolean isWarnEnabled(Marker marker) {
        return delegate.isWarnEnabled(marker);
    }

    /**
     * Log a message with the specific Marker at the WARN level.
     *
     * @param marker The marker specific to this log statement
     * @param msg the message string to be logged
     */
    @Override
    public void warn(Marker marker, String msg) {
        delegate.warn(marker, injectLoggerFormatting(msg));
    }

    /**
     * This method is similar to {@link #warn(String, Object)} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void warn(Marker marker, String format, Object arg) {
        delegate.warn(marker, injectLoggerFormatting(format), arg);
    }

    /**
     * This method is similar to {@link #warn(String, Object, Object)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        delegate.warn(marker, injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * This method is similar to {@link #warn(String, Object...)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        delegate.warn(marker, injectLoggerFormatting(format), arguments);
    }

    /**
     * This method is similar to {@link #warn(String, Throwable)} method
     * except that the marker data is also taken into consideration.
     *
     * @param marker the marker data for this log statement
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        delegate.warn(marker, injectLoggerFormatting(msg), t);
    }

    /**
     * Is the logger instance enabled for the ERROR level?
     *
     * @return True if this Logger is enabled for the ERROR level,
     *         false otherwise.
     */
    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    /**
     * Log a message at the ERROR level.
     *
     * @param msg the message string to be logged
     */
    @Override
    public void error(String msg) {
        delegate.error(injectLoggerFormatting(msg));
    }

    /**
     * Log a message at the ERROR level according to the specified format
     * and argument.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the ERROR level.
     *
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void error(String format, Object arg) {
        delegate.error(injectLoggerFormatting(format), arg);
    }

    /**
     * Log a message at the ERROR level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous object creation when the logger
     * is disabled for the ERROR level.
     *
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void error(String format, Object arg1, Object arg2) {
        delegate.error(injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * Log a message at the ERROR level according to the specified format
     * and arguments.
     * <p>
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the ERROR level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an <code>Object[]</code> before invoking the method,
     * even if this logger is disabled for ERROR. The variants taking
     * {@link #error(String, Object) one} and {@link #error(String, Object, Object) two}
     * arguments exist solely in order to avoid this hidden cost.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void error(String format, Object... arguments) {
        delegate.error(injectLoggerFormatting(format), arguments);
    }

    /**
     * Log an exception (throwable) at the ERROR level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void error(String msg, Throwable t) {
        delegate.error(injectLoggerFormatting(msg), t);
    }

    /**
     * Similar to {@link #isErrorEnabled()} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker The marker data to take into consideration
     * @return True if this Logger is enabled for the ERROR level,
     *         false otherwise.
     */
    @Override
    public boolean isErrorEnabled(Marker marker) {
        return delegate.isErrorEnabled(marker);
    }

    /**
     * Log a message with the specific Marker at the ERROR level.
     *
     * @param marker The marker specific to this log statement
     * @param msg the message string to be logged
     */
    @Override
    public void error(Marker marker, String msg) {
        delegate.error(marker, injectLoggerFormatting(msg));
    }

    /**
     * This method is similar to {@link #error(String, Object)} method except that the
     * marker data is also taken into consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg the argument
     */
    @Override
    public void error(Marker marker, String format, Object arg) {
        delegate.error(marker, injectLoggerFormatting(format), arg);
    }

    /**
     * This method is similar to {@link #error(String, Object, Object)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arg1 the first argument
     * @param arg2 the second argument
     */
    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        delegate.error(marker, injectLoggerFormatting(format), arg1, arg2);
    }

    /**
     * This method is similar to {@link #error(String, Object...)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    @Override
    public void error(Marker marker, String format, Object... arguments) {
        delegate.error(marker, injectLoggerFormatting(format), arguments);
    }

    /**
     * This method is similar to {@link #error(String, Throwable)}
     * method except that the marker data is also taken into
     * consideration.
     *
     * @param marker the marker data specific to this log statement
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    @Override
    public void error(Marker marker, String msg, Throwable t) {
        delegate.error(marker, injectLoggerFormatting(msg), t);
    }
}
