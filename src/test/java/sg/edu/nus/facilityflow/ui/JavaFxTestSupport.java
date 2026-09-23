package sg.edu.nus.facilityflow.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/** Shares the JavaFX toolkit for the test JVM; individual tests must not exit it. */
public final class JavaFxTestSupport {
    private static boolean initialized;

    private JavaFxTestSupport() {
    }

    private static synchronized void initialize() {
        if (!initialized) {
            try {
                Platform.startup(() -> { });
            } catch (IllegalStateException alreadyStarted) {
                // Another UI test or framework already initialized this JVM's toolkit.
            }
            initialized = true;
        }
    }

    /** Runs assertions on the FX thread and propagates failures to the calling test. */
    public static void runOnFxThread(Runnable assertions) throws Exception {
        initialize();
        if (Platform.isFxApplicationThread()) {
            Platform.setImplicitExit(false);
            assertions.run();
            return;
        }
        var result = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                Platform.setImplicitExit(false);
                assertions.run();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        result.get(20, TimeUnit.SECONDS);
    }
}
