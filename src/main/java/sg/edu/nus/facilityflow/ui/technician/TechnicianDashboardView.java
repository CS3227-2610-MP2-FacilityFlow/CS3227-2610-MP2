package sg.edu.nus.facilityflow.ui.technician;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.ui.UiTasks;

/** TEC-001/004/008/009/013: Technician queue and progress actions. */
public final class TechnicianDashboardView extends BorderPane {
    private final TechnicianDashboardController controller;
    private final UiTasks tasks;
    private final Consumer<Throwable> failure;
    private final Button refreshButton = new Button("Refresh requests");
    private final TableView<MaintenanceRequest> requestTable = new TableView<>();
    private final Button startButton = new Button("_Start work");
    private final ListView<String> workLogHistory = new ListView<>();
    private final TextArea workLogNote = new TextArea();
    private final TextField workLogMinutes = new TextField();
    private final Button addWorkLogButton = new Button("_Add work log");
    private final TextArea resolutionSummary = new TextArea();
    private final Button completeWorkButton = new Button("_Submit for Manager review");
    private final Label feedback = new Label();
    private final Label selectedId = new Label("Select a request");
    private final Label selectedDetails = new Label("Choose a row to inspect its details.");
    private boolean busy;
    private boolean loadingWorkLogs;
    private boolean hasWorkLogs;
    private long selectionVersion;

    public TechnicianDashboardView(
            TechnicianDashboardController controller,
            UiTasks tasks,
            Consumer<Throwable> failure) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.failure = Objects.requireNonNull(failure, "failure");
        setId("technicianDashboard");
        getStyleClass().add("technician-shell");
        configureTable();
        configureLayout();
        refresh();
    }

    @SuppressWarnings("unchecked")
    private void configureTable() {
        requestTable.setId("technicianRequests");
        requestTable.getColumns().addAll(
                textColumn("Request ID", MaintenanceRequest::displayId, 110),
                textColumn("Title", MaintenanceRequest::title, 220),
                textColumn("Location", MaintenanceRequest::location, 170),
                textColumn("Manager priority", request -> request.managerPriority() == null
                        ? "Not set" : display(request.managerPriority()), 130),
                textColumn("Reported urgency", request -> display(request.reportedUrgency()), 135),
                textColumn("Status", request -> display(request.status()), 120));
        requestTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        requestTable.setMinHeight(160);
        requestTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        requestTable.setPlaceholder(new Label("No requests are currently assigned to you."));
        requestTable.setAccessibleText("Technician assigned requests");
        requestTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldRequest, newRequest) -> showSelection(newRequest));
    }

    private void configureLayout() {
        Label heading = new Label("Technician work queue");
        heading.getStyleClass().add("page-title");
        Label queueHelp = new Label(
                "Select an assigned request to inspect it and start work when it is ready.");
        queueHelp.getStyleClass().add("secondary-text");
        queueHelp.setWrapText(true);

        refreshButton.setId("refreshTechnicianRequests");
        refreshButton.setOnAction(event -> refresh());
        VBox tableArea = new VBox(8, heading, queueHelp, refreshButton, requestTable);
        VBox.setVgrow(requestTable, Priority.ALWAYS);
        tableArea.setPadding(new Insets(24, 12, 24, 24));
        tableArea.setMinWidth(0);
        tableArea.setMinHeight(240);

        selectedId.setId("technicianRequestDetail");
        selectedId.getStyleClass().add("section-title");
        selectedId.setWrapText(true);
        selectedDetails.setId("technicianRequestDetails");
        selectedDetails.setWrapText(true);
        selectedDetails.getStyleClass().add("secondary-text");

        startButton.setId("startWork");
        startButton.setMnemonicParsing(true);
        startButton.getStyleClass().add("primary-button");
        startButton.setMaxWidth(Double.MAX_VALUE);
        startButton.setOnAction(event -> startSelectedRequest());
        startButton.setDisable(true);

        workLogHistory.setId("technicianWorkLogs");
        workLogHistory.setPlaceholder(new Label("No work logs recorded yet."));
        workLogHistory.setPrefHeight(150);
        workLogHistory.setAccessibleText("Internal technician work logs");

        workLogNote.setId("technicianWorkLogNote");
        workLogNote.setPromptText("Describe the work performed");
        workLogNote.setWrapText(true);
        workLogNote.setPrefRowCount(3);

        workLogMinutes.setId("technicianWorkLogMinutes");
        workLogMinutes.setPromptText("Whole minutes, 1 to 1440");

        addWorkLogButton.setId("addWorkLog");
        addWorkLogButton.setMnemonicParsing(true);
        addWorkLogButton.setOnAction(event -> addWorkLog());
        addWorkLogButton.setDisable(true);

        resolutionSummary.setId("technicianResolutionSummary");
        resolutionSummary.setPromptText("Summarise the resolution for Manager review");
        resolutionSummary.setWrapText(true);
        resolutionSummary.setPrefRowCount(4);

        completeWorkButton.setId("completeWork");
        completeWorkButton.setMnemonicParsing(true);
        completeWorkButton.getStyleClass().add("primary-button");
        completeWorkButton.setMaxWidth(Double.MAX_VALUE);
        completeWorkButton.setOnAction(event -> completeSelectedWork());
        completeWorkButton.setDisable(true);

        feedback.setId("technicianFeedback");
        feedback.setWrapText(true);
        feedback.setAccessibleRoleDescription("Technician action result");
        feedback.getStyleClass().add("feedback-label");

        VBox detailArea = new VBox(
                12,
                new Label("Request detail"),
                selectedId,
                selectedDetails,
                new Separator(Orientation.HORIZONTAL),
                startButton,
                new Label("Internal work history"),
                workLogHistory,
                new Label("Add accountable progress"),
                workLogNote,
                workLogMinutes,
                addWorkLogButton,
                new Label("Resolution summary for Manager review"),
                resolutionSummary,
                completeWorkButton,
                feedback);
        detailArea.setPadding(new Insets(24));
        detailArea.setMinWidth(300);
        detailArea.getStyleClass().add("detail-panel");

        ScrollPane detailScroll = new ScrollPane(detailArea);
        detailScroll.setFitToWidth(true);
        detailScroll.setMinWidth(300);
        detailScroll.setMinHeight(160);

        SplitPane content = new SplitPane(tableArea, detailScroll);
        content.setId("technicianLayout");
        content.setOrientation(Orientation.VERTICAL);
        rootOrientation(content);
        setCenter(content);
    }

    private void rootOrientation(SplitPane content) {
        widthProperty().addListener((observable, previous, width) -> {
            Orientation orientation = width.doubleValue() < 1100
                    ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            if (content.getOrientation() != orientation) {
                content.setOrientation(orientation);
                content.setDividerPositions(orientation == Orientation.HORIZONTAL ? 0.7 : 0.5);
            }
        });
        content.setDividerPositions(0.7);
    }

    private void refresh() {
        refresh("");
    }

    private void refresh(String confirmation) {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        setBusy(true);
        feedback.setText("Loading requests…");
        tasks.run(controller::loadRequests, requests -> {
            requestTable.setItems(FXCollections.observableArrayList(requests));
            if (selected != null) {
                requests.stream()
                        .filter(request -> request.id() == selected.id())
                        .findFirst()
                        .ifPresentOrElse(
                                requestTable.getSelectionModel()::select,
                                requestTable.getSelectionModel()::clearSelection);
            } else {
                requestTable.getSelectionModel().clearSelection();
            }
            setBusy(false);
            showSuccess(confirmation);
        }, error -> {
            setBusy(false);
            showError(UiTasks.safeMessage(error));
            failure.accept(error);
        });
    }

    private void showSelection(MaintenanceRequest request) {
        selectionVersion++;
        long requestSelection = selectionVersion;
        if (request == null) {
            loadingWorkLogs = false;
            hasWorkLogs = false;
            workLogHistory.getItems().clear();
            selectedId.setText("Select a request");
            selectedDetails.setText("Choose a row to inspect its details.");
        } else {
            loadingWorkLogs = true;
            workLogHistory.getItems().clear();
            workLogHistory.setPlaceholder(new Label("Loading work history…"));
            selectedId.setText(request.displayId() + " · " + request.title());
            selectedDetails.setText("Location: " + request.location()
                    + "\nStatus: " + display(request.status())
                    + "\nManager priority: " + (request.managerPriority() == null
                            ? "Not set" : display(request.managerPriority()))
                    + "\n\n" + request.description());
            tasks.run(() -> controller.loadWorkLogs(request), logs -> {
                if (requestSelection != selectionVersion) {
                    return;
                }
                loadingWorkLogs = false;
                hasWorkLogs = !logs.isEmpty();
                workLogHistory.setPlaceholder(new Label("No work logs recorded yet."));
                workLogHistory.setItems(FXCollections.observableArrayList(
                        logs.stream().map(TechnicianDashboardView::formatWorkLog).toList()));
                updateActionState();
            }, error -> {
                if (requestSelection != selectionVersion) {
                    return;
                }
                loadingWorkLogs = false;
                hasWorkLogs = false;
                updateActionState();
                if (isStaleAssignment(error)) {
                    refresh("The request assignment changed. The queue was refreshed.");
                } else {
                    showError(UiTasks.safeMessage(error));
                    failure.accept(error);
                }
            });
        }
        updateActionState();
    }

    private void updateActionState() {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        startButton.setDisable(busy || selected == null
                || selected.status() != RequestStatus.ASSIGNED);
        addWorkLogButton.setDisable(busy || loadingWorkLogs || selected == null
                || selected.status() != RequestStatus.IN_PROGRESS);
        completeWorkButton.setDisable(busy || loadingWorkLogs || !hasWorkLogs || selected == null
                || selected.status() != RequestStatus.IN_PROGRESS);
    }

    private void startSelectedRequest() {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RequestStatus.ASSIGNED) {
            return;
        }
        setBusy(true);
        feedback.setText("Starting work…");
        tasks.run(() -> controller.startWork(selected), started -> {
            refresh(started.displayId() + " is now IN_PROGRESS. Work started successfully.");
        }, error -> {
            setBusy(false);
            if (isStaleAssignment(error)) {
                refresh("The request assignment or status changed. The queue was refreshed.");
            } else {
                showError(UiTasks.safeMessage(error));
                failure.accept(error);
            }
        });
    }

    private void addWorkLog() {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RequestStatus.IN_PROGRESS) {
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(workLogMinutes.getText().strip());
        } catch (NumberFormatException error) {
            showError("Minutes spent must be a whole number from 1 to 1,440.");
            return;
        }
        setBusy(true);
        feedback.setText("Saving work log…");
        tasks.run(() -> controller.addWorkLog(selected, workLogNote.getText(), minutes), log -> {
            workLogNote.clear();
            workLogMinutes.clear();
            refresh(selected.displayId() + " work log saved successfully.");
        }, error -> {
            setBusy(false);
            if (isStaleAssignment(error)) {
                refresh("The request assignment or status changed. The queue was refreshed.");
            } else {
                showError(UiTasks.safeMessage(error));
                failure.accept(error);
            }
        });
    }

    private void completeSelectedWork() {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RequestStatus.IN_PROGRESS || !hasWorkLogs) {
            return;
        }
        setBusy(true);
        feedback.setText("Submitting work for Manager review…");
        tasks.run(() -> controller.completeWork(selected, resolutionSummary.getText()), completed -> {
            resolutionSummary.clear();
            refresh(completed.displayId() + " was submitted for Manager review successfully.");
        }, error -> {
            setBusy(false);
            if (isStaleAssignment(error)) {
                refresh("The request assignment or status changed. The queue was refreshed.");
            } else {
                showError(UiTasks.safeMessage(error));
                failure.accept(error);
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        refreshButton.setDisable(value);
        requestTable.setDisable(value);
        updateActionState();
    }

    private void showSuccess(String message) {
        feedback.getStyleClass().remove("feedback-error");
        if (!feedback.getStyleClass().contains("feedback-success")) {
            feedback.getStyleClass().add("feedback-success");
        }
        feedback.setText(message);
    }

    private void showError(String message) {
        feedback.getStyleClass().remove("feedback-success");
        if (!feedback.getStyleClass().contains("feedback-error")) {
            feedback.getStyleClass().add("feedback-error");
        }
        feedback.setText(message);
    }

    private static boolean isStaleAssignment(Throwable error) {
        return error instanceof AuthorizationException
                && error.getMessage() != null
                && (error.getMessage().startsWith("The request assignment or status changed.")
                        || error.getMessage().equals("Request is unavailable."));
    }

    private static TableColumn<MaintenanceRequest, String> textColumn(
            String title,
            java.util.function.Function<MaintenanceRequest, String> value,
            double width) {
        TableColumn<MaintenanceRequest, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    private static String formatWorkLog(WorkLog log) {
        return log.createdAt() + " · Technician #" + log.authorId()
                + " · " + log.minutesSpent() + " minutes\n" + log.note();
    }

    private static String display(Enum<?> value) {
        String text = value.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
