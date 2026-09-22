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
        var heading = new Label("Technician");
        heading.getStyleClass().add("page-title");
        var notice = new Label("Technician work management is not available in this development build.");
        notice.setWrapText(true);
        notice.getStyleClass().add("secondary-text");
        getChildren().addAll(heading, notice);
    }
}
