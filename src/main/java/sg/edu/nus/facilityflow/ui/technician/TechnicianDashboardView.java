package sg.edu.nus.facilityflow.ui.technician;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Separate route until the Technician owner's work-management UI is integrated. */
public final class TechnicianDashboardView extends VBox {
    public TechnicianDashboardView() {
        super(12);
        setId("technicianDashboard");
        setPadding(new Insets(24));
        getChildren().addAll(new Label("Technician"),
                new Label("Technician work management is not available in this development build."));
    }
}
