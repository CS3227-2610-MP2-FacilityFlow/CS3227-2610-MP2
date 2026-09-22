package sg.edu.nus.facilityflow.ui.requester;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.ui.UiTasks;

/** Owner-only navigation; all persistence happens in background service calls. */
public final class RequesterDashboardView extends BorderPane {
    private final RequesterRequestService service;
    private AuthenticatedSession session;
    private final List<String> categories;
    private final UiTasks tasks;
    private final Consumer<Throwable> failure;
    private final ListView<MaintenanceRequest> list = new ListView<>();
    private final Label feedback = new Label();
    private final VBox listPane;

    public RequesterDashboardView(RequesterRequestService service, AuthenticatedSession session,
                                  List<String> categories, UiTasks tasks, Consumer<Throwable> failure) {
        this.service = service;
        this.session = session;
        this.categories = categories;
        this.tasks = tasks;
        this.failure = failure;
        setId("requesterDashboard");
        setPadding(new Insets(20));
        list.setId("ownRequests");
        list.setAccessibleText("My requests: ID, title, status");
        list.setPlaceholder(new Label("You have no requests yet."));
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(MaintenanceRequest request, boolean empty) {
                super.updateItem(request, empty);
                setText(empty || request == null ? null : request.displayId() + " — "
                        + request.title() + " — " + request.status());
            }
        });
        var create = new Button("New request");
        create.setId("newRequest");
        create.setOnAction(event -> showForm());
        var refresh = new Button("Refresh requests");
        refresh.setId("refreshRequests");
        refresh.setOnAction(event -> refresh());
        var open = new Button("View selected request");
        open.setId("viewRequest");
        open.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        open.setOnAction(event -> loadDetail(list.getSelectionModel().getSelectedItem().id()));
        feedback.setId("requesterFeedback");
        feedback.setWrapText(true);
        listPane = new VBox(12, new Label("My requests — ID, title, status"),
                new HBox(10, create, refresh, open), list, feedback);
        VBox.setVgrow(list, Priority.ALWAYS);
        setCenter(listPane);
        refresh();
    }

    public void resume(AuthenticatedSession replacement) {
        session = replacement;
    }

    private void refresh() {
        listPane.setDisable(true);
        feedback.setText("Loading requests…");
        var caller = session;
        tasks.run(() -> service.listOwnRequests(caller), requests -> {
            list.getItems().setAll(requests);
            listPane.setDisable(false);
            feedback.setText(requests.size() + " request(s)");
        }, error -> {
            listPane.setDisable(false);
            feedback.setText(UiTasks.safeMessage(error));
            failure.accept(error);
        });
    }

    private void showForm() {
        var form = new RequesterForm(categories, draft -> save(draft));
        form.setId("requesterForm");
        var back = new Button("Back to my requests");
        back.setOnAction(event -> showList());
        // The entire form/navigation is disabled while saving, so input cannot be discarded mid-submit.
        back.disableProperty().bind(form.disabledProperty());
        var scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        var pane = new BorderPane(scroll);
        pane.setTop(back);
        setCenter(pane);
    }

    private void save(sg.edu.nus.facilityflow.model.RequestDraft draft) {
        var form = (RequesterForm) lookup("#requesterForm");
        var caller = session;
        tasks.run(() -> service.createRequest(caller, draft), created -> {
            form.setSubmitting(false);
            showDetail(created, created.displayId() + " was saved successfully.");
        }, error -> {
            form.showFailure(UiTasks.safeMessage(error));
            failure.accept(error);
        });
    }

    private void loadDetail(long id) {
        var caller = session;
        var loadingPane = getCenter();
        loadingPane.setDisable(true);
        feedback.setText("Loading request…");
        tasks.run(() -> service.getOwnRequest(caller, id), request -> {
            loadingPane.setDisable(false);
            showDetail(request, "");
        }, error -> {
            loadingPane.setDisable(false);
            feedback.setText(UiTasks.safeMessage(error));
            failure.accept(error);
        });
    }

    private void showDetail(MaintenanceRequest request, String message) {
        var back = new Button("Back to my requests");
        back.setId("backToRequests");
        back.setOnAction(event -> showList());
        var refresh = new Button("Refresh detail");
        refresh.setOnAction(event -> loadDetail(request.id()));
        var dates = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z").withZone(ZoneId.systemDefault());
        var detail = new Label(request.displayId() + " — " + request.title()
                + "\nStatus: " + request.status() + "\nReported urgency: " + request.reportedUrgency()
                + "\nManager priority: " + (request.managerPriority() == null ? "Not set" : request.managerPriority())
                + "\nCategory: " + request.category() + "\nLocation: " + request.location()
                + "\nCreated: " + dates.format(request.createdAt()) + "\nUpdated: " + dates.format(request.updatedAt())
                + "\n\n" + request.description());
        detail.setWrapText(true);
        detail.setId("requestDetail");
        feedback.setText(message);
        // Feedback belongs to only one parent at a time.
        if (feedback.getParent() instanceof VBox parent) {
            parent.getChildren().remove(feedback);
        }
        var pane = new VBox(15, new HBox(10, back, refresh), detail, feedback);
        var scroll = new ScrollPane(pane);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private void showList() {
        if (feedback.getParent() instanceof VBox parent) {
            parent.getChildren().remove(feedback);
        }
        listPane.getChildren().add(feedback);
        setCenter(listPane);
        refresh();
    }
}
