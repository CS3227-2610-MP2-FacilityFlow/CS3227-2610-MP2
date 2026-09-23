package sg.edu.nus.facilityflow.ui;

import java.util.concurrent.ExecutionException;
import javafx.application.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxTestSupportTest {
    @Test
    @DisplayName("QLT-006 supports an already-started toolkit and subsequent UI assertions")
    void reusesToolkitAndPropagatesFailures() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // This test must also pass when another UI class runs first.
        }
        JavaFxTestSupport.runOnFxThread(() -> {
            assertTrue(Platform.isFxApplicationThread());
            assertFalse(Platform.isImplicitExit());
        });
        var failure = assertThrows(ExecutionException.class,
                () -> JavaFxTestSupport.runOnFxThread(() -> {
                    throw new AssertionError("Expected regression-test failure");
                }));
        assertInstanceOf(AssertionError.class, failure.getCause());
        JavaFxTestSupport.runOnFxThread(() -> assertTrue(Platform.isFxApplicationThread()));
    }
}
