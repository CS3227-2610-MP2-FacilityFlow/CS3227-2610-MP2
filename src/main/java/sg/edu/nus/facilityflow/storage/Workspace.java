package sg.edu.nus.facilityflow.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import sg.edu.nus.facilityflow.util.OperationalLog;

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
            OperationalLog.warning(
                    "category_catalogue", "validation_failed", "reason=malformed_properties");
            throw new IOException("Invalid categories.properties. Correct it before restarting.", exception);
        }
        var categories = parseNames(properties.getProperty("categories", ""), "categories");
        if (categories.stream().anyMatch(String::isBlank) || !categories.contains("Other")
                || categories.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != categories.size()) {
            OperationalLog.warning(
                    "category_catalogue", "validation_failed", "reason=invalid_categories");
            throw new IOException("Invalid categories.properties. Use unique categories including Other.");
        }
        var migrations = parseMigrations(properties, categories);
        OperationalLog.info(
                "category_catalogue", "validation_complete",
                "categories=" + categories.size() + " mappings=" + migrations.size());
        var store = new SQLiteManagerAssignmentStore("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
        store.initializeWorkspace(categories, migrations);
        return new Workspace(store, categories);
    }

    private static Map<String, String> parseMigrations(Properties properties, List<String> categories) throws IOException {
        var allowed = java.util.Set.of("categories", "renames", "removals");
        if (!allowed.containsAll(properties.stringPropertyNames())) {
            throw invalidMappings();
        }
        var migrations = new LinkedHashMap<String, String>();
        for (String mapping : parseOptionalNames(properties.getProperty("renames"))) {
            int separator = mapping.indexOf('>');
            if (separator < 1 || separator != mapping.lastIndexOf('>')) {
                throw invalidMappings();
            }
            addMigration(migrations, mapping.substring(0, separator).strip(),
                    mapping.substring(separator + 1).strip(), categories);
        }
        for (String removed : parseOptionalNames(properties.getProperty("removals"))) {
            addMigration(migrations, removed, "Other", categories);
        }
        return Map.copyOf(migrations);
    }

    private static List<String> parseOptionalNames(String value) throws IOException {
        if (value == null) {
            return List.of();
        }
        var names = parseNames(value, "mapping");
        if (names.stream().anyMatch(String::isBlank)) {
            throw invalidMappings();
        }
        return names;
    }

    private static List<String> parseNames(String value, String field) throws IOException {
        if (value.isBlank()) {
            if (field.equals("mapping")) {
                throw invalidMappings();
            }
            return List.of();
        }
        return Arrays.stream(value.split(",", -1)).map(String::strip).toList();
    }

    private static void addMigration(Map<String, String> migrations, String source, String target,
                                     List<String> categories) throws IOException {
        if (source.isBlank() || !categories.contains(target) || categories.contains(source)
                || migrations.putIfAbsent(source, target) != null
                || migrations.keySet().stream().filter(name -> name.equalsIgnoreCase(source)).count() > 1) {
            throw invalidMappings();
        }
    }

    private static IOException invalidMappings() {
        OperationalLog.warning(
                "category_catalogue", "validation_failed", "reason=invalid_mappings");
        return new IOException("Invalid category rename/removal mappings. Rename entries use Old>New; "
                + "removals list old categories to move to Other. Targets must be configured categories.");
    }
}
