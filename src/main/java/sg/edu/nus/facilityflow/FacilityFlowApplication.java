package sg.edu.nus.facilityflow;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.application.Platform;
import javafx.stage.Stage;
import sg.edu.nus.facilityflow.auth.AuthenticationService;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.service.ManagerRequestService;
import sg.edu.nus.facilityflow.service.ManagerAccountService;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.service.RequestValidator;
import sg.edu.nus.facilityflow.service.TechnicianRequestService;
import sg.edu.nus.facilityflow.storage.StorageException;
import sg.edu.nus.facilityflow.storage.Workspace;
import sg.edu.nus.facilityflow.ui.ApplicationRouter;
import sg.edu.nus.facilityflow.ui.UiTasks;
import sg.edu.nus.facilityflow.util.OperationalLog;

public final class FacilityFlowApplication extends Application {
    private final SessionManager sessions = new SessionManager();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("facilityflow-worker").factory());

    @Override
    public void start(Stage stage) {
        try {
            OperationalLog.initialize(Workspace.defaultDirectory(), AppVersion.CURRENT);
        } catch (IOException exception) {
            System.err.println("FacilityFlow could not initialize its rotating operational log.");
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            OperationalLog.unexpected("ui", "uncaught_exception", error);
            Platform.runLater(() -> {
                var alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("FacilityFlow error");
                alert.setHeaderText("An unexpected error occurred");
                alert.setContentText("Your saved data was not intentionally discarded. "
                        + "Return to the previous screen or restart FacilityFlow.");
                alert.showAndWait();
            });
        });
        var root = new BorderPane(new Label("Opening FacilityFlow…"));
        var scene = new Scene(root, 1024, 700);
        scene.getStylesheets().add(FacilityFlowApplication.class
                .getResource("/sg/edu/nus/facilityflow/ui/facilityflow.css").toExternalForm());
        stage.setTitle("FacilityFlow");
        stage.setScene(scene);
        stage.setMinWidth(780);
        stage.setMinHeight(640);
        stage.show();
        var tasks = new UiTasks(executor);
        tasks.run(() -> Workspace.open(Workspace.defaultDirectory()), workspace -> {
            var clock = Clock.systemUTC();
            var auth = new AuthenticationService(workspace.store(), sessions, clock);
            var validator = new RequestValidator(Set.copyOf(workspace.categories()));
            var requester = new RequesterRequestService(
                    workspace.store(), validator, clock, sessions);
            var manager = new ManagerRequestService(
                    workspace.store(), clock, sessions, validator);
            var managerAccounts = new ManagerAccountService(workspace.store(), clock, sessions);
            var technician = new TechnicianRequestService(workspace.store(), clock, sessions);
            OperationalLog.info("workspace", "startup_complete", "schema=validated");
            scene.setRoot(new ApplicationRouter(
                    auth, requester, manager, managerAccounts, technician,
                    workspace.categories(), tasks));
        }, error -> {
            String detail;
            if (error instanceof IOException) {
                detail = error.getMessage();
            } else if (error instanceof StorageException) {
                detail = "The local database could not be opened safely. Close other copies of "
                        + "FacilityFlow, check folder access, or restore a compatible backup.";
            } else {
                detail = "Check database access, the application version, and categories.properties.";
            }
            var message = new Label("FacilityFlow could not open its workspace.\n"
                    + detail + "\n" + Workspace.defaultDirectory());
            message.setWrapText(true);
            root.setCenter(message);
            System.getLogger(FacilityFlowApplication.class.getName()).log(System.Logger.Level.ERROR,
                    "event=startup_failed component=workspace type=" + error.getClass().getSimpleName());
            OperationalLog.unexpected("workspace", "startup_failed", error);
        });
    }

    @Override
    public void stop() {
        OperationalLog.info("application", "shutdown", "status=normal");
        OperationalLog.close();
        sessions.close();
        executor.shutdown();
    }
}
