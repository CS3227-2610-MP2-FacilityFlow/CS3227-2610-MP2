package sg.edu.nus.facilityflow.ui.manager;

import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import sg.edu.nus.facilityflow.ui.UiTasks;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.UserAccount;
import java.util.function.Consumer;



/**
 * Accessible Manager request queue and assignment view for MGR-001 and MGR-004–005.
 * It is wired only after authentication supplies an authorized controller.
 */
public final class ManagerDashboardView {
    private final ManagerDashboardController controller;
    private final UiTasks tasks;
    private final Consumer<Throwable> failure;
    private boolean busy;
    private final Button refreshButton = new Button("Refresh requests");
    private final BorderPane root = new BorderPane();
    private final TableView<MaintenanceRequest> requestTable = new TableView<>();
    private final ComboBox<UserAccount> technicianBox = new ComboBox<>();
    private final ComboBox<ManagerPriority> priorityBox = new ComboBox<>();
    private final Button assignButton = new Button("_Assign request");
    private final Label feedback = new Label();
    private final Label selectedId = new Label("Select a request");
    private final Label selectedDescription = new Label("Choose a row to inspect its details.");

    public ManagerDashboardView(ManagerDashboardController controller, UiTasks tasks, Consumer<Throwable> failure) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.tasks = tasks;
        this.failure = failure;
        root.setId("managerDashboard");
        root.getStyleClass().add("manager-shell");
        configureTable();
        configureAssignmentPanel();
        refresh();
    }
    public Parent root() {
        return root;
    }

    @SuppressWarnings("unchecked")
    private void configureTable() {
        requestTable.setId("managerRequests");
        TableColumn<MaintenanceRequest, String> idColumn = textColumn(
                "Request ID", request -> request.displayId());
        idColumn.setPrefWidth(110);
        TableColumn<MaintenanceRequest, String> titleColumn = textColumn(
                "Title", MaintenanceRequest::title);
        titleColumn.setPrefWidth(220);
        TableColumn<MaintenanceRequest, String> locationColumn = textColumn(
                "Location", MaintenanceRequest::location);
        locationColumn.setPrefWidth(170);
        TableColumn<MaintenanceRequest, String> urgencyColumn = textColumn(
                "Reported urgency", request -> display(request.reportedUrgency()));
        urgencyColumn.setPrefWidth(135);
        TableColumn<MaintenanceRequest, String> priorityColumn = textColumn(
                "Manager priority",
                request -> request.managerPriority() == null
                        ? "Not set"
                        : display(request.managerPriority()));
        priorityColumn.setPrefWidth(130);
        TableColumn<MaintenanceRequest, String> statusColumn = textColumn(
                "Status", request -> display(request.status()));
        statusColumn.setPrefWidth(110);

        requestTable.getColumns().addAll(
                idColumn, titleColumn, locationColumn, urgencyColumn, priorityColumn, statusColumn);
        requestTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        requestTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        requestTable.setPlaceholder(new Label("No maintenance requests are available."));
        requestTable.setAccessibleText("All maintenance requests");
        requestTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldRequest, newRequest) -> showSelection(newRequest));
    }

    private void configureAssignmentPanel() {
        Label queueTitle = new Label("All requests");
        queueTitle.getStyleClass().add("section-title");
        Label queueHelp = new Label(
                "OPEN requests are ordered by reported urgency and age. Select one to triage it.");
        queueHelp.getStyleClass().add("secondary-text");
        queueHelp.setWrapText(true);
        refreshButton.setOnAction(event -> refresh());
        VBox tableArea = new VBox(6, queueTitle, queueHelp, refreshButton, requestTable);
        VBox.setVgrow(requestTable, Priority.ALWAYS);
        tableArea.setPadding(new Insets(24, 12, 24, 24));

        selectedId.getStyleClass().add("section-title");
        selectedDescription.setWrapText(true);
        selectedDescription.getStyleClass().add("secondary-text");

        Label technicianLabel = new Label("_Technician (required)");
        technicianLabel.setMnemonicParsing(true);
        technicianLabel.setLabelFor(technicianBox);
        technicianBox.setAccessibleText("Active Technician");
        technicianBox.setId("assignee");
        technicianBox.setPromptText("Select an active Technician");
        technicianBox.setMaxWidth(Double.MAX_VALUE);
        technicianBox.setConverter(new UserAccountStringConverter());

        Label priorityLabel = new Label("_Manager priority (required)");
        priorityLabel.setMnemonicParsing(true);
        priorityLabel.setLabelFor(priorityBox);
        priorityBox.setAccessibleText("Manager priority");
        priorityBox.setId("managerPriority");
        priorityBox.setPromptText("Select priority");
        priorityBox.setItems(FXCollections.observableArrayList(ManagerPriority.values()));
        priorityBox.setMaxWidth(Double.MAX_VALUE);

        assignButton.setMnemonicParsing(true);
        assignButton.setId("assignRequest");
        assignButton.setDefaultButton(true);
        assignButton.getStyleClass().add("primary-button");
        assignButton.setMaxWidth(Double.MAX_VALUE);
        assignButton.setOnAction(event -> assignSelectedRequest());
        assignButton.setDisable(true);

        feedback.setWrapText(true);
        feedback.setAccessibleRoleDescription("Assignment result");
        feedback.getStyleClass().add("feedback-label");

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.add(technicianLabel, 0, 0);
        form.add(technicianBox, 0, 1);
        form.add(priorityLabel, 0, 2);
        form.add(priorityBox, 0, 3);
        form.add(assignButton, 0, 4);
        form.add(feedback, 0, 5);
        GridPane.setHgrow(technicianBox, Priority.ALWAYS);
        GridPane.setHgrow(priorityBox, Priority.ALWAYS);

        technicianBox.valueProperty().addListener((observable, oldValue, newValue) -> updateAssignState());
        priorityBox.valueProperty().addListener((observable, oldValue, newValue) -> updateAssignState());

        VBox detailArea = new VBox(
                12,
                new Label("Request detail"),
                selectedId,
                selectedDescription,
                new Separator(Orientation.HORIZONTAL),
                new Label("Assignment"),
                form);
        detailArea.setPadding(new Insets(24, 24, 24, 12));
        detailArea.setMinWidth(310);
        detailArea.setMaxWidth(380);
        detailArea.getStyleClass().add("detail-panel");

        SplitPane content = new SplitPane(tableArea, detailArea);
        content.setDividerPositions(0.7);
        root.setCenter(content);
    }

    private record QueueData(java.util.List<MaintenanceRequest> requests, java.util.List<UserAccount> technicians) {
    }

    private void setBusy(boolean value) {
        busy = value;
        refreshButton.setDisable(value);
        requestTable.setDisable(value);
        technicianBox.setDisable(value);
        priorityBox.setDisable(value);
        updateAssignState();
    }

    private void refresh() {
        refresh("");
    }

    private void refresh(String confirmation) {
        var selected = requestTable.getSelectionModel().getSelectedItem();
        setBusy(true);
        feedback.setText("Loading requests…");
        tasks.run(() -> new QueueData(controller.loadRequests(), controller.loadActiveTechnicians()), data -> {
            requestTable.setItems(FXCollections.observableArrayList(data.requests()));
            technicianBox.setItems(FXCollections.observableArrayList(data.technicians()));
            if (selected != null) {
                data.requests().stream().filter(request -> request.id() == selected.id())
                        .findFirst().ifPresent(requestTable.getSelectionModel()::select);
            }
            setBusy(false);
            feedback.setText(confirmation);
        }, error -> {
            setBusy(false);
            showError(confirmation.isEmpty() ? UiTasks.safeMessage(error)
                    : confirmation + " The queue could not be refreshed. Please refresh it again.");
            failure.accept(error);
        });
    }
    private void showSelection(MaintenanceRequest request) {
        if (request == null) {
            selectedId.setText("Select a request");
            selectedDescription.setText("Choose a row to inspect its details.");
        } else {
            selectedId.setText(request.displayId() + " · " + request.title());
            selectedDescription.setText(request.description());
        }
        updateAssignState();
    }

    private void updateAssignState() {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        assignButton.setDisable(
                busy || selected == null
                        || selected.status() != RequestStatus.OPEN
                        || technicianBox.getValue() == null
                        || priorityBox.getValue() == null);
    }

    private void assignSelectedRequest() {
        var selected = requestTable.getSelectionModel().getSelectedItem();
        var technician = technicianBox.getValue();
        var priority = priorityBox.getValue();
        setBusy(true);
        feedback.setText("Assigning request…");
        tasks.run(() -> controller.assign(selected, technician, priority), assigned -> {
            int index = requestTable.getItems().indexOf(selected);
            if (index >= 0) {
                requestTable.getItems().set(index, assigned);
            }
            requestTable.getSelectionModel().select(assigned);
            setBusy(false);
            feedback.getStyleClass().removeAll("feedback-error");
            feedback.getStyleClass().add("feedback-success");
            refresh(assigned.displayId() + " was assigned successfully.");
        }, error -> {
            setBusy(false);
            showError(UiTasks.safeMessage(error));
            failure.accept(error);
        });
    }
    private void showError(String message) {
        feedback.getStyleClass().removeAll("feedback-success", "feedback-error");
        feedback.getStyleClass().add("feedback-error");
        feedback.setText(message);
    }

    private static TableColumn<MaintenanceRequest, String> textColumn(
            String title, java.util.function.Function<MaintenanceRequest, String> value) {
        TableColumn<MaintenanceRequest, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setSortable(false);
        return column;
    }

    private static String display(Enum<?> value) {
        String text = value.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
