package sg.edu.nus.facilityflow;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Application shell. Authentication will replace this integration notice. */
public final class FacilityFlowApplication extends Application {
    @Override
    public void start(Stage stage) {
        Label title = new Label("FacilityFlow");
        title.getStyleClass().add("shell-title");
        Label notice = new Label(
                "The project foundation is ready. Connect the shared authentication flow "
                        + "before opening a role dashboard.");
        notice.setWrapText(true);
        VBox root = new VBox(12, title, notice);
        root.setPadding(new Insets(32));
        root.getStyleClass().add("application-shell");

        Scene scene = new Scene(root, 1024, 700);
        scene.getStylesheets().add(
                FacilityFlowApplication.class.getResource("/sg/edu/nus/facilityflow/ui/facilityflow.css")
                        .toExternalForm());
        stage.setTitle("FacilityFlow");
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
