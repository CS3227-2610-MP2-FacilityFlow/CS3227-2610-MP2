package sg.edu.nus.facilityflow.ui.technician;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import sg.edu.nus.facilityflow.ui.UiTasks;

/** Technician work-management route; controls are added as each lifecycle action is delivered. */
public final class TechnicianDashboardView extends BorderPane {
    public TechnicianDashboardView(
            TechnicianDashboardController controller,
            UiTasks tasks,
            Consumer<Throwable> failure) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(failure, "failure");
        setId("technicianDashboard");
        setPadding(new Insets(24));
        var heading = new Label("Technician");
        heading.getStyleClass().add("page-title");
        var notice = new Label("Technician work management is being enabled.");
        notice.setWrapText(true);
        notice.getStyleClass().add("secondary-text");
        setTop(heading);
        setCenter(notice);
    }
}
