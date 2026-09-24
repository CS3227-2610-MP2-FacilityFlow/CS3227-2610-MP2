package sg.edu.nus.facilityflow.ui.requester;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
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
        showForm(null);
    }

    private void showForm(MaintenanceRequest request) {
        var form = new RequesterForm(categories, draft -> save(draft, request), request);
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

    private void save(RequestDraft draft, MaintenanceRequest request) {
        var form = (RequesterForm) lookup("#requesterForm");
        var caller = session;
        tasks.run(() -> request == null ? service.createRequest(caller, draft)
                : service.editRequest(caller, request.id(), draft), created -> {
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
        var actions = new FlowPane(10, 10, back, refresh);
        if (request.status() == RequestStatus.OPEN) {
            var edit = new Button("Edit request");
            edit.setId("editRequest");
            edit.setOnAction(event -> showForm(request));
            var cancel = new Button("Cancel request");
            cancel.setId("cancelRequest");
            cancel.setOnAction(event -> showCancellation(request));
            actions.getChildren().addAll(edit, cancel);
        }
        var pane = new VBox(15, actions, heading, detail, description, feedback);
        if (request.status() == RequestStatus.CANCELLED) {
            var reason = new Label("Loading cancellation reason…");
            reason.setId("cancellationReason");
            reason.setWrapText(true);
            pane.getChildren().add(reason);
            var caller = session;
            tasks.run(() -> service.getOwnCancellationReason(caller, request.id()),
                    value -> reason.setText(value.map(text -> "Cancellation reason: " + text).orElse("")),
                    error -> {
                        reason.setText(UiTasks.safeMessage(error));
                        failure.accept(error);
                    });
        }
        var scroll = new ScrollPane(pane);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    /** REQ-008/012: a distinct confirmation screen keeps the reason on failed cancellation. */
    private void showCancellation(MaintenanceRequest request) {
        var heading = new Label("Cancel " + request.displayId() + "?");
        heading.getStyleClass().add("page-title");
        var reason = new TextArea();
        reason.setId("cancelReason");
        reason.setWrapText(true);
        reason.setPrefRowCount(4);
        var label = new Label("Cancellation reason * (5–500 characters)");
        label.setLabelFor(reason);
        reason.setAccessibleText(label.getText());
        var notice = new Label("Confirm cancellation of this request. Cancelled requests cannot be edited or reopened.");
        notice.setWrapText(true);
        var result = new Label();
        result.setId("cancelFeedback");
        result.setWrapText(true);
        var back = new Button("Keep request");
        back.setId("keepRequest");
        back.setOnAction(event -> showDetail(request, ""));
        var confirm = new Button("Confirm cancellation");
        confirm.setId("confirmCancellation");
        var pane = new VBox(15, heading, notice, label, reason,
                new FlowPane(10, 10, back, confirm), result);
        confirm.setOnAction(event -> {
            var text = reason.getText().strip();
            int length = text.codePointCount(0, text.length());
            if (length < 5 || length > 500) {
                result.setText("Enter a cancellation reason of 5–500 characters after trimming.");
                return;
            }
            var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Cancel " + request.displayId() + " — " + request.title()
                            + "? It will become CANCELLED and cannot be edited or reopened.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Confirm request cancellation");
            confirmation.setHeaderText("Cancel this request?");
            confirmation.getDialogPane().setId("cancelConfirmation");
            if (getScene() != null && getScene().getWindow() != null) {
                confirmation.initOwner(getScene().getWindow());
            }
            var cancelButton = (Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL);
            cancelButton.setText("Keep request");
            var confirmButton = (Button) confirmation.getDialogPane().lookupButton(ButtonType.OK);
            confirmButton.setText("Cancel request");
            confirmButton.setId("confirmCancelDialog");
            confirmButton.setDefaultButton(false);
            cancelButton.setDefaultButton(true);
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
            pane.setDisable(true);
            result.setText("Cancelling request…");
            var caller = session;
            tasks.run(() -> service.cancelRequest(caller, request.id(), text), saved -> {
                pane.setDisable(false);
                showDetail(saved, saved.displayId() + " was cancelled successfully.");
            }, error -> {
                pane.setDisable(false);
                result.setText(UiTasks.safeMessage(error));
                failure.accept(error);
            });
        });
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
