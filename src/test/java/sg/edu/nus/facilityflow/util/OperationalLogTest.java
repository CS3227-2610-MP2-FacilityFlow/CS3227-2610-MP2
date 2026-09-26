package sg.edu.nus.facilityflow.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperationalLogTest {
    @TempDir
    Path workspace;

    @AfterEach
    void closeLog() {
        OperationalLog.close();
    }

    @Test
    @DisplayName("OBS-001/002/005 writes structured versioned diagnostics in the rotating workspace log")
    void writesStructuredStartupAndApplicationEvents() throws Exception {
        OperationalLog.initialize(workspace, "1.2.3-test");
        OperationalLog.warning("database", "connection_unavailable", "requestId=FF-000123");
        OperationalLog.close();

        List<Path> files;
        try (var paths = Files.list(workspace.resolve("logs"))) {
            files = paths.toList();
        }
        assertTrue(files.stream().allMatch(path -> path.getFileName().toString()
                .matches("facilityflow-\\d+\\.log(?:\\.\\d+)?")), files.toString());
        assertTrue(files.size() <= 5, "the configured rotating family must retain at most five logs");
        String log = readLogs(files);
        assertTrue(log.matches("(?s).*timestamp=\\d{4}-\\d{2}-\\d{2}T.*"), log);
        assertTrue(log.contains("severity=INFO component=application event=startup version=1.2.3-test"), log);
        assertTrue(log.contains(
                "severity=WARNING component=database event=connection_unavailable requestId=FF-000123"), log);
    }

    @Test
    @DisplayName("OBS-003 sanitizes control characters and omits exception messages containing secrets or request text")
    void sanitizesFieldsAndDoesNotWriteExceptionMessage() throws Exception {
        OperationalLog.initialize(workspace, "1.0\nforged");
        OperationalLog.info("manager\nforged", "request\tupdated", "requestId=FF-000123\rstatus=CLOSED");
        OperationalLog.unexpected(
                "storage", "write_failed",
                new IllegalStateException("password=Secret123 fullDescription=private request text"));
        OperationalLog.close();

        List<Path> files;
        try (var paths = Files.list(workspace.resolve("logs"))) {
            files = paths.toList();
        }
        String log = readLogs(files);
        assertTrue(log.contains("version=1.0 forged"), log);
        assertTrue(log.contains("component=manager forged event=request updated "), log);
        assertTrue(log.contains("exception=java.lang.IllegalStateException"), log);
        assertFalse(log.contains("Secret123"), log);
        assertFalse(log.contains("private request text"), log);
        for (String line : log.lines().toList()) {
            assertTrue(line.startsWith("timestamp="), "each physical line must be one structured event: " + line);
        }
    }

    private static String readLogs(List<Path> files) throws Exception {
        StringBuilder content = new StringBuilder();
        for (Path file : files) {
            content.append(Files.readString(file));
        }
        return content.toString();
    }
}
