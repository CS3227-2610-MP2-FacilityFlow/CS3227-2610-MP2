package facilityflow.ui.requester;

import facilityflow.ui.JavaFxTestSupport;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequesterFormTest {
    @RepeatedTest(2)
    @DisplayName("REQ-012/UIX-007–008 preview retains input, shows errors, and clears corrected errors")
    void validatesWithoutDiscardingInput() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            var form = new RequesterForm(List.of("Electrical"));
            var title = (TextField) form.lookup("#title");
            var description = (TextArea) form.lookup("#description");
            var location = (TextField) form.lookup("#location");
            var category = (ComboBox<?>) form.lookup("#category");
            var urgency = (ComboBox<?>) form.lookup("#urgency");
            var button = (Button) form.lookup("#validate");
            title.setText("  Keep my input  ");
            button.fire();
            assertEquals("  Keep my input  ", title.getText());
            assertTrue(form.lookup("#descriptionError").isVisible());
            assertTrue(form.lookup("#categoryError").isVisible());
            assertTrue(form.lookup("#urgencyError").isVisible());
            description.setText("The light flickers.");
            location.setText("Room 12");
            category.getSelectionModel().selectFirst();
            urgency.getSelectionModel().selectFirst();
            button.fire();
            assertFalse(form.lookup("#descriptionError").isVisible());
            assertFalse(form.lookup("#categoryError").isVisible());
            assertFalse(form.lookup("#urgencyError").isVisible());
            assertEquals("  Keep my input  ", title.getText());
            assertTrue(((Label) form.lookup("#feedback")).getText().contains("Nothing has been saved"));
        });
    }
}
