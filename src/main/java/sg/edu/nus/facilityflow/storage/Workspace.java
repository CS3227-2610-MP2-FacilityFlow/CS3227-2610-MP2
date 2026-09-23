package sg.edu.nus.facilityflow.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** DAT-001: one workspace per OS user; category validation precedes any database migration. */
public record Workspace(SQLiteManagerAssignmentStore store, List<String> categories) {
    private static final String DEFAULT_CATEGORIES = "Electrical,Plumbing,HVAC,Structural,Cleaning,Safety,Other";

    public static Path defaultDirectory() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            return (local == null ? Path.of(home, "AppData", "Local") : Path.of(local)).resolve("FacilityFlow");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "FacilityFlow");
        }
        String data = System.getenv("XDG_DATA_HOME");
        return (data == null || data.isBlank() ? Path.of(home, ".local", "share") : Path.of(data)).resolve("FacilityFlow");
    }

    public static Workspace open(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path catalogue = directory.resolve("categories.properties");
        if (Files.notExists(catalogue)) {
            Files.writeString(catalogue, "categories=" + DEFAULT_CATEGORIES + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        }
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(catalogue, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid categories.properties. Correct it before restarting.", exception);
        }
        var categories = Arrays.stream(properties.getProperty("categories", "").split(",", -1))
                .map(String::strip).toList();
        if (!properties.stringPropertyNames().equals(java.util.Set.of("categories"))
                || categories.stream().anyMatch(String::isBlank) || !categories.contains("Other")
                || categories.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != categories.size()) {
            throw new IOException("Invalid categories.properties. Use unique categories including Other. "
                    + "Rename/removal mappings require the pending category-migration implementation.");
        }
        var store = new SQLiteManagerAssignmentStore("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
        store.initializeWorkspace(categories);
        return new Workspace(store, categories);
    }
}
