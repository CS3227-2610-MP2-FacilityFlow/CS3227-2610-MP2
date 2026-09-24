package sg.edu.nus.facilityflow;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import sg.edu.nus.facilityflow.auth.AuthenticationService;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.service.ManagerRequestService;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.service.RequestValidator;
import sg.edu.nus.facilityflow.service.TechnicianRequestService;
import sg.edu.nus.facilityflow.storage.Workspace;
import sg.edu.nus.facilityflow.ui.ApplicationRouter;
import sg.edu.nus.facilityflow.ui.UiTasks;

public final class FacilityFlowApplication extends Application {
    private final SessionManager sessions = new SessionManager();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("facilityflow-worker").factory());

    @Override
    public void start(Stage stage) {
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
            var requester = new RequesterRequestService(workspace.store(),
                    new RequestValidator(Set.copyOf(workspace.categories())), clock, sessions);
            var manager = new ManagerRequestService(workspace.store(), clock, sessions);
            var technician = new TechnicianRequestService(workspace.store(), clock, sessions);
            scene.setRoot(new ApplicationRouter(
                    auth, requester, manager, technician, workspace.categories(), tasks));
        }, error -> {
            var message = new Label("FacilityFlow could not open its workspace.\n"
                    + "Check database access, the application version, and categories.properties, then restart.\n"
                    + Workspace.defaultDirectory());
            message.setWrapText(true);
            root.setCenter(message);
            System.getLogger(FacilityFlowApplication.class.getName()).log(System.Logger.Level.ERROR,
                    "event=startup_failed component=workspace type=" + error.getClass().getSimpleName());
        });
    }

    @Override
    public void stop() {
        sessions.close();
        executor.shutdown();
    }
}
