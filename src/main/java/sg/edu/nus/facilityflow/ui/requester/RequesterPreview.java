package sg.edu.nus.facilityflow.ui.requester;

import java.util.List;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

/** Standalone form preview until shared login and persistence are integrated. */
public final class RequesterPreview extends Application {
    @Override
    public void start(Stage stage) {
        // Preview fixture only; production categories must come from the shared catalogue.
        var categories = List.of("Electrical", "Plumbing", "HVAC", "Structural",
                "Cleaning", "Safety", "Other");
        stage.setTitle("FacilityFlow — Requester preview");
        var content = new ScrollPane(new RequesterForm(categories));
        content.setFitToWidth(true);
        stage.setScene(new Scene(content, 1024, 700));
        stage.setMinWidth(800);
        stage.setMinHeight(650);
        stage.show();
    }
}
