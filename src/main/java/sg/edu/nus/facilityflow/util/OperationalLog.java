package sg.edu.nus.facilityflow.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Rotating structured diagnostics that never receive passwords or request free text. */
public final class OperationalLog {
    private static final Logger LOGGER = Logger.getLogger("sg.edu.nus.facilityflow");
    private static FileHandler fileHandler;
    private static boolean initialized;

    private OperationalLog() {
    }

    public static synchronized void initialize(Path workspace, String version) throws IOException {
        if (initialized) {
            return;
        }
        Path directory = workspace.resolve("logs");
        Files.createDirectories(directory);
        FileHandler handler = new FileHandler(
                directory.resolve("facilityflow-%g.log").toString(), 1_000_000, 5, true);
        handler.setEncoding("UTF-8");
        handler.setFormatter(new StructuredFormatter());
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
        fileHandler = handler;
        initialized = true;
        info("application", "startup", "version=" + safe(version));
    }

    /** Flushes the active file cleanly so shutdown and tests do not lose the final records. */
    public static synchronized void close() {
        if (fileHandler == null) {
            return;
        }
        fileHandler.flush();
        fileHandler.close();
        LOGGER.removeHandler(fileHandler);
        fileHandler = null;
        initialized = false;
    }

    public static void info(String component, String event, String message) {
        log(Level.INFO, component, event, message, null);
    }

    public static void warning(String component, String event, String message) {
        log(Level.WARNING, component, event, message, null);
    }

    public static void unexpected(String component, String event, Throwable error) {
        String type = error == null ? "unknown" : error.getClass().getSimpleName();
        log(Level.SEVERE, component, event, "type=" + safe(type), error);
    }

    private static void log(
            Level level, String component, String event, String message, Throwable error) {
        LogRecord record = new LogRecord(level, "component=" + safe(component)
                + " event=" + safe(event) + " " + safe(message));
        record.setLoggerName(LOGGER.getName());
        record.setThrown(error);
        LOGGER.log(record);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ");
    }

    private static final class StructuredFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder output = new StringBuilder("timestamp=")
                    .append(Instant.ofEpochMilli(record.getMillis()))
                    .append(" severity=").append(record.getLevel().getName())
                    .append(' ').append(formatMessage(record));
            Throwable error = record.getThrown();
            if (error != null) {
                appendDiagnosticContext(output, error);
            }
            return output.append(System.lineSeparator()).toString();
        }

        private static void appendDiagnosticContext(StringBuilder output, Throwable error) {
            output.append(" exception=").append(safe(error.getClass().getName()));
            StackTraceElement[] trace = error.getStackTrace();
            int limit = Math.min(trace.length, 12);
            for (int index = 0; index < limit; index++) {
                output.append(" frame").append(index).append('=')
                        .append(safe(trace[index].toString()));
            }
            Throwable cause = error.getCause();
            if (cause != null && cause != error) {
                output.append(" cause=").append(safe(cause.getClass().getName()));
            }
        }
    }
}
