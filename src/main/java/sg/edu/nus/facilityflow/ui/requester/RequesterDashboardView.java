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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
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
            private final Label title = new Label();
            private final Label summary = new Label();
            private final VBox content = new VBox(5, title, summary);

            {
                title.getStyleClass().add("request-title");
                summary.getStyleClass().add("secondary-text");
                title.setWrapText(true);
                summary.setWrapText(true);
                content.maxWidthProperty().bind(list.widthProperty().subtract(64));
            }

            @Override
            protected void updateItem(MaintenanceRequest request, boolean empty) {
                super.updateItem(request, empty);
                setText(null);
                if (empty || request == null) {
                    setGraphic(null);
                    setAccessibleText(null);
                } else {
                    title.setText(request.title());
                    summary.setText(request.displayId() + "  ·  " + request.status() + "  ·  " + request.location());
                    setGraphic(content);
                    setAccessibleText(title.getText() + ". " + summary.getText());
                }
            }
        });
        var create = new Button("New request");
        create.setId("newRequest");
        create.getStyleClass().add("primary-button");
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
        var heading = new Label("My requests");
        heading.getStyleClass().add("page-title");
        var help = new Label("Track your maintenance requests. Select a request to see its details.");
        help.setWrapText(true);
        help.getStyleClass().add("secondary-text");
        feedback.getStyleClass().add("feedback-label");
        listPane = new VBox(12, heading, help,
                new FlowPane(10, 10, create, refresh, open), list, feedback);
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
        var container = new StackPane(form);
        container.setPadding(new Insets(16, 0, 0, 0));
        var scroll = new ScrollPane(container);
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
        var heading = new Label(request.title());
        heading.setWrapText(true);
        heading.getStyleClass().add("page-title");
        var description = new Label(request.description());
        description.setWrapText(true);
        var detail = new Label(request.displayId()
                + "\nStatus: " + request.status() + "\nReported urgency: " + request.reportedUrgency()
                + "\nManager priority: " + (request.managerPriority() == null ? "Not set" : request.managerPriority())
                + "\nCategory: " + request.category() + "\nLocation: " + request.location()
                + "\nCreated: " + dates.format(request.createdAt()) + "\nUpdated: " + dates.format(request.updatedAt()));
        detail.setWrapText(true);
        detail.setId("requestDetail");
        detail.getStyleClass().add("request-detail");
        detail.setMaxWidth(Double.MAX_VALUE);
        feedback.setText(message);
        // Feedback belongs to only one parent at a time.
        if (feedback.getParent() instanceof VBox parent) {
            parent.getChildren().remove(feedback);
        }
        var pane = new VBox(15, new FlowPane(10, 10, back, refresh), heading, detail, description, feedback);
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
