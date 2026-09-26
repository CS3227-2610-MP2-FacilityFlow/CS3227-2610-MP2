package sg.edu.nus.facilityflow.ui.manager;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sg.edu.nus.facilityflow.model.AuditFilter;
import sg.edu.nus.facilityflow.model.AuditRecord;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerDashboardSummary;
import sg.edu.nus.facilityflow.model.ManagerHistoryEntry;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ManagerRequestFilter;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.TechnicianWorkload;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.ui.UiTasks;

/** Complete Facilities Manager workspace for MGR-001–015 and MGR-018–021. */
public final class ManagerDashboardView {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z")
            .withZone(ZoneId.systemDefault());

    private final ManagerDashboardController controller;
    private final List<String> categories;
    private final UiTasks tasks;
    private final Consumer<Throwable> failure;
    private final BorderPane root = new BorderPane();
    private final Label feedback = new Label();
    private final TableView<MaintenanceRequest> requestTable = new TableView<>();
    private final TableView<UserAccount> accountTable = new TableView<>();
    private final TableView<AuditRecord> auditTable = new TableView<>();
    private final TableView<TechnicianWorkload> workloadTable = new TableView<>();
    private final ListView<String> history = new ListView<>();
    private final ComboBox<UserAccount> technicianBox = new ComboBox<>();
    private final ComboBox<ManagerPriority> priorityBox = new ComboBox<>();
    private final ComboBox<UserAccount> technicianFilter = new ComboBox<>();
    private final ComboBox<RequestStatus> statusFilter = new ComboBox<>();
    private final ComboBox<String> categoryFilter = new ComboBox<>();
    private final ComboBox<ManagerPriority> priorityFilter = new ComboBox<>();
    private final TextField search = new TextField();
    private final DatePicker requestFrom = new DatePicker();
    private final DatePicker requestThrough = new DatePicker();
    private final TextArea reason = new TextArea();
    private final Label selectedDetails = new Label("Select a request to inspect its details and history.");
    private final Label statusCounts = new Label();
    private final Label priorityCounts = new Label();
    private final TextField auditRequest = new TextField();
    private final TextField auditActor = new TextField();
    private final TextField auditAction = new TextField();
    private final DatePicker auditFrom = new DatePicker();
    private final DatePicker auditThrough = new DatePicker();
    private final Button assignButton = new Button("_Assign request");
    private final Button reassignButton = new Button("_Reassign");
    private final Button closeButton = new Button("_Close completed work");
    private final Button returnButton = new Button("_Return for rework");
    private final Button reopenButton = new Button("Re_open closed request");
    private final Button cancelButton = new Button("_Cancel request");
    private boolean busy;
    private List<UserAccount> requesters = List.of();

    public ManagerDashboardView(
            ManagerDashboardController controller,
            List<String> categories,
            UiTasks tasks,
            Consumer<Throwable> failure) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.categories = List.copyOf(categories);
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.failure = Objects.requireNonNull(failure, "failure");
        root.setId("managerDashboard");
        root.getStyleClass().add("manager-shell");
        feedback.setId("managerFeedback");
        feedback.setWrapText(true);
        feedback.getStyleClass().add("feedback-label");
        TabPane tabs = new TabPane(
                fixedTab("Overview", buildOverview()),
                fixedTab("Requests", buildRequests()),
                fixedTab("Accounts", buildAccounts()),
                fixedTab("Audit", buildAudit()));
        tabs.setId("managerTabs");
        tabs.getSelectionModel().select(1);
        root.setCenter(tabs);
        root.setBottom(feedback);
        BorderPane.setMargin(feedback, new Insets(4, 20, 12, 20));
        refreshAll("");
    }

    public Parent root() {
        return root;
    }

    private Parent buildOverview() {
        statusCounts.setId("managerStatusCounts");
        priorityCounts.setId("managerPriorityCounts");
        statusCounts.getStyleClass().add("summary-card");
        priorityCounts.getStyleClass().add("summary-card");
        statusCounts.setWrapText(true);
        priorityCounts.setWrapText(true);
        configureWorkloadTable();
        workloadTable.setPlaceholder(new Label("No active Technicians are available."));
        workloadTable.setAccessibleText("Active assignments per Technician");
        Button refresh = new Button("_Refresh overview");
        refresh.setMnemonicParsing(true);
        refresh.setOnAction(event -> refreshAll("Overview refreshed."));
        FlowPane cards = new FlowPane(12, 12, statusCounts, priorityCounts);
        VBox content = new VBox(
                14,
                section("Operational overview"),
                new Label("Current workload counts across all maintenance requests."),
                cards,
                section("Active assignments per Technician"),
                workloadTable,
                refresh);
        VBox.setVgrow(workloadTable, Priority.ALWAYS);
        return padded(content);
    }

    @SuppressWarnings("unchecked")
    private void configureWorkloadTable() {
        workloadTable.getColumns().addAll(
                textColumn("Technician", TechnicianWorkload::displayName),
                textColumn("Active assignments", item -> Integer.toString(item.activeAssignments())));
        workloadTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private Parent buildRequests() {
        configureRequestTable();
        VBox queue = new VBox(10, section("All requests"), buildRequestFilters(), requestTable);
        VBox.setVgrow(requestTable, Priority.ALWAYS);
        VBox detail = buildRequestDetail();
        ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        detailScroll.setMinWidth(320);
        SplitPane split = new SplitPane(queue, detailScroll);
        split.setId("managerLayout");
        split.setDividerPositions(0.62);
        split.setOrientation(Orientation.HORIZONTAL);
        root.widthProperty().addListener((observable, oldWidth, width) -> {
            Orientation orientation = width.doubleValue() < 1_050
                    ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            if (split.getOrientation() != orientation) {
                split.setOrientation(orientation);
                split.setDividerPositions(orientation == Orientation.HORIZONTAL ? 0.62 : 0.48);
            }
        });
        VBox wrapper = new VBox(10, requestToolbar(), split);
        VBox.setVgrow(split, Priority.ALWAYS);
        return padded(wrapper);
    }

    private Parent requestToolbar() {
        Button record = new Button("_Record request on behalf");
        record.setMnemonicParsing(true);
        record.setId("recordOnBehalf");
        record.getStyleClass().add("primary-button");
        record.setOnAction(event -> showRequestDialog(null));
        Button correct = new Button("Correct selected _details");
        correct.setMnemonicParsing(true);
        correct.setId("correctRequest");
        correct.setOnAction(event -> showRequestDialog(requestTable.getSelectionModel().getSelectedItem()));
        correct.disableProperty().bind(requestTable.getSelectionModel().selectedItemProperty().isNull());
        return new HBox(10, record, correct);
    }

    @SuppressWarnings("unchecked")
    private void configureRequestTable() {
        requestTable.setId("managerRequests");
        requestTable.getColumns().addAll(
                textColumn("Request ID", MaintenanceRequest::displayId),
                textColumn("Title", MaintenanceRequest::title),
                textColumn("Location", MaintenanceRequest::location),
                textColumn("Urgency", item -> display(item.reportedUrgency())),
                textColumn("Priority", item -> item.managerPriority() == null
                        ? "Not set" : display(item.managerPriority())),
                textColumn("Status", item -> display(item.status())));
        requestTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        requestTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        requestTable.setPlaceholder(new Label("No requests match the current search and filters."));
        requestTable.setAccessibleText("All maintenance requests");
        requestTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> showSelection(selected));
    }

    private Parent buildRequestFilters() {
        search.setId("managerSearch");
        search.setPromptText("ID, title, location, Requester, or Technician");
        statusFilter.setId("managerStatusFilter");
        statusFilter.setItems(withNull(RequestStatus.values()));
        statusFilter.setPromptText("Any status");
        categoryFilter.setId("managerCategoryFilter");
        categoryFilter.setItems(withNull(categories));
        categoryFilter.setPromptText("Any category");
        priorityFilter.setId("managerPriorityFilter");
        priorityFilter.setItems(withNull(ManagerPriority.values()));
        priorityFilter.setPromptText("Any priority");
        technicianFilter.setId("managerTechnicianFilter");
        technicianFilter.setPromptText("Any Technician");
        technicianFilter.setConverter(new UserAccountStringConverter());
        requestFrom.setId("managerFromDate");
        requestThrough.setId("managerThroughDate");
        Button apply = new Button("_Apply filters");
        apply.setMnemonicParsing(true);
        apply.setId("applyManagerFilters");
        apply.setOnAction(event -> refreshRequests("Filters applied."));
        Button reset = new Button("_Reset filters");
        reset.setMnemonicParsing(true);
        reset.setId("resetManagerFilters");
        reset.setOnAction(event -> {
            search.clear();
            statusFilter.setValue(null);
            categoryFilter.setValue(null);
            priorityFilter.setValue(null);
            technicianFilter.setValue(null);
            requestFrom.setValue(null);
            requestThrough.setValue(null);
            refreshRequests("Filters reset.");
        });
        Button refresh = new Button("Refresh _requests");
        refresh.setMnemonicParsing(true);
        refresh.setId("refreshManagerRequests");
        refresh.setOnAction(event -> refreshRequests("Requests refreshed."));
        GridPane filters = new GridPane();
        filters.setHgap(8);
        filters.setVgap(6);
        addLabeled(filters, 0, "_Search", search);
        addLabeled(filters, 1, "S_tatus", statusFilter);
        addLabeled(filters, 2, "Cate_gory", categoryFilter);
        addLabeled(filters, 3, "_Priority", priorityFilter);
        addLabeled(filters, 4, "Techn_ician", technicianFilter);
        addLabeled(filters, 5, "_From", requestFrom);
        addLabeled(filters, 6, "Throu_gh", requestThrough);
        filters.add(new HBox(8, apply, reset, refresh), 0, 2, 7, 1);
        return filters;
    }

    private VBox buildRequestDetail() {
        selectedDetails.setId("managerRequestDetails");
        selectedDetails.setWrapText(true);
        history.setId("managerRequestHistory");
        history.setPrefHeight(180);
        history.setPlaceholder(new Label("No request history is available."));
        technicianBox.setId("assignee");
        technicianBox.setPromptText("Select an active Technician");
        technicianBox.setConverter(new UserAccountStringConverter());
        technicianBox.setMaxWidth(Double.MAX_VALUE);
        priorityBox.setId("managerPriority");
        priorityBox.setItems(FXCollections.observableArrayList(ManagerPriority.values()));
        priorityBox.setPromptText("Select priority");
        priorityBox.setMaxWidth(Double.MAX_VALUE);
        reason.setId("managerReason");
        reason.setPromptText("Reason required for reassignment, return, reopen, or cancellation");
        reason.setPrefRowCount(3);
        configureAction(assignButton, "assignRequest", event -> assign());
        configureAction(reassignButton, "reassignRequest", event -> reassign());
        configureAction(closeButton, "closeRequest", event -> close());
        configureAction(returnButton, "returnRequest", event -> returnForRework());
        configureAction(reopenButton, "reopenRequest", event -> reopen());
        configureAction(cancelButton, "cancelManagerRequest", event -> cancel());
        cancelButton.getStyleClass().add("danger-button");
        technicianBox.valueProperty().addListener((observable, oldValue, value) -> updateActions());
        priorityBox.valueProperty().addListener((observable, oldValue, value) -> updateActions());
        reason.textProperty().addListener((observable, oldValue, value) -> updateActions());
        FlowPane actions = new FlowPane(8, 8,
                assignButton, reassignButton, closeButton, returnButton, reopenButton, cancelButton);
        return new VBox(
                10,
                section("Request detail"),
                selectedDetails,
                new Separator(),
                section("Full history"),
                history,
                new Separator(),
                section("Lifecycle actions"),
                labeled("_Technician", technicianBox),
                labeled("Manager _priority", priorityBox),
                labeled("_Reason", reason),
                actions);
    }

    private Parent buildAccounts() {
        configureAccountTable();
        TextField username = new TextField();
        username.setId("newAccountUsername");
        TextField displayName = new TextField();
        displayName.setId("newAccountDisplayName");
        ComboBox<Role> role = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        role.setId("newAccountRole");
        PasswordField password = new PasswordField();
        password.setId("newAccountPassword");
        Button create = new Button("_Create account");
        create.setMnemonicParsing(true);
        create.setId("createAccount");
        create.getStyleClass().add("primary-button");
        create.setOnAction(event -> {
            char[] secret = password.getText().toCharArray();
            password.clear();
            runChange(() -> controller.createAccount(
                    username.getText(), displayName.getText(), role.getValue(), secret),
                    account -> {
                        username.clear();
                        displayName.clear();
                        role.setValue(null);
                        refreshAccounts("Account " + account.username() + " was created.");
                    });
        });
        GridPane createForm = new GridPane();
        createForm.setHgap(8);
        createForm.setVgap(8);
        addLabeled(createForm, 0, "_Username", username);
        addLabeled(createForm, 1, "Display _name", displayName);
        addLabeled(createForm, 2, "_Role", role);
        addLabeled(createForm, 3, "Initial _password", password);
        createForm.add(create, 0, 2, 4, 1);

        ComboBox<Role> newRole = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        newRole.setId("accountRoleChange");
        Button changeRole = new Button("Change _role");
        changeRole.setMnemonicParsing(true);
        changeRole.setOnAction(event -> selectedAccount().ifPresent(account -> runChange(
                () -> controller.changeRole(account, newRole.getValue()),
                changed -> refreshAccounts("Role changed for " + changed.username() + "."))));
        Button toggleActive = new Button("Deactivate / reactivate");
        toggleActive.setId("toggleAccountActive");
        toggleActive.setOnAction(event -> selectedAccount().ifPresent(account -> {
            String verb = account.active() ? "deactivate" : "reactivate";
            if (confirm("Confirm account change", "Do you want to " + verb
                    + " " + account.username() + "?")) {
                runChange(() -> controller.setActive(account, !account.active()),
                        changed -> refreshAccounts("Account " + changed.username()
                                + " is now " + (changed.active() ? "active." : "inactive.")));
            }
        }));
        PasswordField reset = new PasswordField();
        reset.setId("resetAccountPassword");
        Button resetPassword = new Button("Reset _password");
        resetPassword.setMnemonicParsing(true);
        resetPassword.setOnAction(event -> selectedAccount().ifPresent(account -> {
            char[] secret = reset.getText().toCharArray();
            reset.clear();
            runChange(() -> {
                controller.resetPassword(account, secret);
                return account;
            }, changed -> refreshAccounts("Password reset for " + changed.username() + "."));
        }));
        FlowPane actions = new FlowPane(8, 8, newRole, changeRole, toggleActive, reset, resetPassword);
        VBox content = new VBox(
                12,
                section("Account administration"),
                new Label("Create accounts and manage the selected account. Accounts are never deleted."),
                createForm,
                new Separator(),
                accountTable,
                actions);
        VBox.setVgrow(accountTable, Priority.ALWAYS);
        return padded(content);
    }

    @SuppressWarnings("unchecked")
    private void configureAccountTable() {
        accountTable.setId("managerAccounts");
        accountTable.getColumns().addAll(
                textColumn("Username", UserAccount::username),
                textColumn("Display name", UserAccount::displayName),
                textColumn("Role", account -> display(account.role())),
                textColumn("Status", account -> account.active() ? "Active" : "Inactive"));
        accountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        accountTable.setPlaceholder(new Label("No accounts are available."));
    }

    private Parent buildAudit() {
        configureAuditTable();
        Button apply = new Button("_Apply audit filters");
        apply.setMnemonicParsing(true);
        apply.setId("applyAuditFilters");
        apply.setOnAction(event -> refreshAudit("Audit filters applied."));
        Button reset = new Button("_Reset audit filters");
        reset.setMnemonicParsing(true);
        reset.setOnAction(event -> {
            auditRequest.clear();
            auditActor.clear();
            auditAction.clear();
            auditFrom.setValue(null);
            auditThrough.setValue(null);
            refreshAudit("Audit filters reset.");
        });
        GridPane filters = new GridPane();
        filters.setHgap(8);
        filters.setVgap(8);
        addLabeled(filters, 0, "_Request", auditRequest);
        addLabeled(filters, 1, "_Actor", auditActor);
        addLabeled(filters, 2, "Action _type", auditAction);
        addLabeled(filters, 3, "_From", auditFrom);
        addLabeled(filters, 4, "Throu_gh", auditThrough);
        filters.add(new HBox(8, apply, reset), 0, 2, 5, 1);
        VBox content = new VBox(
                12,
                section("Read-only audit trail"),
                new Label("Search immutable events by request, actor, action type, and date."),
                filters,
                auditTable);
        VBox.setVgrow(auditTable, Priority.ALWAYS);
        return padded(content);
    }

    @SuppressWarnings("unchecked")
    private void configureAuditTable() {
        auditTable.setId("managerAudit");
        auditTable.getColumns().addAll(
                textColumn("Time", record -> TIME.format(record.occurredAt())),
                textColumn("Request / target", record -> record.requestDisplayId() == null
                        ? record.targetType() + " " + record.targetId() : record.requestDisplayId()),
                textColumn("Actor", AuditRecord::actorName),
                textColumn("Action", record -> record.action().replace('_', ' ')),
                textColumn("Details", AuditRecord::detail));
        auditTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        auditTable.setPlaceholder(new Label("No audit events match the current filters."));
        auditTable.setAccessibleText("Read-only audit events");
    }

    private void refreshAll(String confirmation) {
        setBusy(true);
        tasks.run(() -> new ManagerData(
                        controller.loadRequests(currentRequestFilter()),
                        controller.loadActiveTechnicians(),
                        controller.loadRequesters(),
                        controller.loadSummary(),
                        controller.loadAccounts(),
                        controller.loadAudit(currentAuditFilter())),
                data -> {
                    setRequestData(data.requests(), data.technicians(), data.requesters());
                    updateSummary(data.summary());
                    accountTable.setItems(FXCollections.observableArrayList(data.accounts()));
                    auditTable.setItems(FXCollections.observableArrayList(data.audit()));
                    setBusy(false);
                    showSuccess(confirmation);
                }, this::handleError);
    }

    private void refreshRequests(String confirmation) {
        setBusy(true);
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        tasks.run(() -> controller.loadRequests(currentRequestFilter()), requests -> {
            requestTable.setItems(FXCollections.observableArrayList(requests));
            restoreSelection(selected);
            setBusy(false);
            showSuccess(confirmation);
        }, this::handleError);
    }

    private void refreshAccounts(String confirmation) {
        setBusy(true);
        tasks.run(controller::loadAccounts, accounts -> {
            accountTable.setItems(FXCollections.observableArrayList(accounts));
            setBusy(false);
            showSuccess(confirmation);
        }, this::handleError);
    }

    private void refreshAudit(String confirmation) {
        setBusy(true);
        tasks.run(() -> controller.loadAudit(currentAuditFilter()), records -> {
            auditTable.setItems(FXCollections.observableArrayList(records));
            setBusy(false);
            showSuccess(confirmation);
        }, this::handleError);
    }

    private void setRequestData(
            List<MaintenanceRequest> requests,
            List<UserAccount> technicians,
            List<UserAccount> activeRequesters) {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        requestTable.setItems(FXCollections.observableArrayList(requests));
        technicianBox.setItems(FXCollections.observableArrayList(technicians));
        technicianFilter.setItems(withNull(technicians));
        technicianFilter.setConverter(new UserAccountStringConverter());
        requesters = List.copyOf(activeRequesters);
        restoreSelection(selected);
    }

    private void updateSummary(ManagerDashboardSummary summary) {
        statusCounts.setText("By status\n" + joinCounts(summary.statusCounts()));
        priorityCounts.setText("By Manager priority\n" + joinCounts(summary.priorityCounts()));
        workloadTable.setItems(FXCollections.observableArrayList(summary.technicianWorkloads()));
    }

    private void showSelection(MaintenanceRequest selected) {
        if (selected == null) {
            selectedDetails.setText("Select a request to inspect its details and history.");
            history.getItems().clear();
        } else {
            selectedDetails.setText(selected.displayId() + " — " + selected.title()
                    + "\nStatus: " + display(selected.status())
                    + " | Reported urgency: " + display(selected.reportedUrgency())
                    + " | Manager priority: " + (selected.managerPriority() == null
                            ? "Not set" : display(selected.managerPriority()))
                    + "\nLocation: " + selected.location()
                    + "\nCategory: " + selected.category()
                    + "\nRequester account: " + selected.requesterId()
                    + " | Technician account: " + (selected.assigneeId() == null
                            ? "Unassigned" : selected.assigneeId())
                    + "\nCreated: " + TIME.format(selected.createdAt())
                    + " | Updated: " + TIME.format(selected.updatedAt())
                    + "\n\n" + selected.description()
                    + (selected.resolutionSummary() == null ? ""
                            : "\n\nResolution: " + selected.resolutionSummary()));
            tasks.run(() -> controller.loadHistory(selected.id()), entries -> {
                if (requestTable.getSelectionModel().getSelectedItem() != null
                        && requestTable.getSelectionModel().getSelectedItem().id() == selected.id()) {
                    history.setItems(FXCollections.observableArrayList(
                            entries.stream().map(ManagerDashboardView::historyText).toList()));
                }
            }, this::handleError);
        }
        updateActions();
    }

    private void updateActions() {
        MaintenanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        boolean hasTechnician = technicianBox.getValue() != null;
        boolean hasReason = !reason.getText().strip().isEmpty();
        assignButton.setDisable(busy || selected == null || selected.status() != RequestStatus.OPEN
                || !hasTechnician || priorityBox.getValue() == null);
        reassignButton.setDisable(busy || selected == null
                || (selected.status() != RequestStatus.ASSIGNED
                        && selected.status() != RequestStatus.IN_PROGRESS)
                || !hasTechnician || !hasReason);
        closeButton.setDisable(busy || selected == null
                || selected.status() != RequestStatus.COMPLETED);
        returnButton.setDisable(busy || selected == null
                || selected.status() != RequestStatus.COMPLETED || !hasTechnician || !hasReason);
        reopenButton.setDisable(busy || selected == null
                || selected.status() != RequestStatus.CLOSED || !hasTechnician || !hasReason);
        cancelButton.setDisable(busy || selected == null || !hasReason
                || (selected.status() != RequestStatus.OPEN
                        && selected.status() != RequestStatus.ASSIGNED
                        && selected.status() != RequestStatus.IN_PROGRESS));
    }

    private void assign() {
        MaintenanceRequest selected = selectedRequest();
        runRequestChange(() -> controller.assign(
                selected, technicianBox.getValue(), priorityBox.getValue()), "Request assigned.");
    }

    private void reassign() {
        MaintenanceRequest selected = selectedRequest();
        runRequestChange(() -> controller.reassign(
                selected, technicianBox.getValue(), reason.getText()), "Request reassigned.");
    }

    private void close() {
        MaintenanceRequest selected = selectedRequest();
        if (confirm("Close completed work", "Close " + selected.displayId()
                + " after reviewing its resolution and work history?")) {
            runRequestChange(() -> controller.close(selected), "Completed work closed.");
        }
    }

    private void returnForRework() {
        MaintenanceRequest selected = selectedRequest();
        runRequestChange(() -> controller.returnForRework(
                selected, technicianBox.getValue(), reason.getText()), "Work returned for rework.");
    }

    private void reopen() {
        MaintenanceRequest selected = selectedRequest();
        runRequestChange(() -> controller.reopen(
                selected, technicianBox.getValue(), reason.getText()), "Request reopened.");
    }

    private void cancel() {
        MaintenanceRequest selected = selectedRequest();
        if (confirm("Cancel request", "Cancel " + selected.displayId()
                + "? Its status will become CANCELLED.")) {
            runRequestChange(() -> controller.cancel(selected, reason.getText()), "Request cancelled.");
        }
    }

    private void runRequestChange(
            java.util.concurrent.Callable<MaintenanceRequest> action, String confirmation) {
        setBusy(true);
        tasks.run(action, changed -> {
            reason.clear();
            refreshAll(changed.displayId() + ": " + confirmation);
        }, this::handleError);
    }

    private <T> void runChange(java.util.concurrent.Callable<T> action, Consumer<T> success) {
        setBusy(true);
        tasks.run(action, result -> {
            setBusy(false);
            success.accept(result);
        }, this::handleError);
    }

    private void showRequestDialog(MaintenanceRequest existing) {
        boolean correction = existing != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(correction ? "Correct request details" : "Record request on behalf");
        ButtonType save = new ButtonType(correction ? "Save correction" : "Record request",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField title = new TextField(correction ? existing.title() : "");
        TextArea description = new TextArea(correction ? existing.description() : "");
        description.setPrefRowCount(4);
        TextField location = new TextField(correction ? existing.location() : "");
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(categories));
        category.setValue(correction ? existing.category() : null);
        ComboBox<ReportedUrgency> urgency = new ComboBox<>(
                FXCollections.observableArrayList(ReportedUrgency.values()));
        urgency.setValue(correction ? existing.reportedUrgency() : null);
        ComboBox<ManagerPriority> priority = new ComboBox<>(
                FXCollections.observableArrayList(ManagerPriority.values()));
        priority.setValue(correction ? existing.managerPriority() : null);
        ComboBox<UserAccount> requester = new ComboBox<>(FXCollections.observableArrayList(requesters));
        requester.setConverter(new UserAccountStringConverter());
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        int row = 0;
        if (!correction) {
            addFormRow(form, row++, "Requester (required)", requester);
        }
        addFormRow(form, row++, "Title (required)", title);
        addFormRow(form, row++, "Description (required)", description);
        addFormRow(form, row++, "Location (required)", location);
        addFormRow(form, row++, "Category (required)", category);
        addFormRow(form, row++, "Reported urgency (required)", urgency);
        if (correction) {
            addFormRow(form, row, "Manager priority", priority);
        }
        dialog.getDialogPane().setContent(form);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == save) {
            RequestDraft draft = new RequestDraft(
                    title.getText(), description.getText(), location.getText(),
                    category.getValue(), urgency.getValue());
            if (correction) {
                runRequestChange(() -> controller.correct(existing, draft, priority.getValue()),
                        "Request details corrected without changing lifecycle status.");
            } else if (requester.getValue() == null) {
                showError("Select an existing Requester account.");
            } else {
                runRequestChange(() -> controller.recordOnBehalf(requester.getValue(), draft),
                        "Request recorded on behalf of " + requester.getValue().displayName() + ".");
            }
        }
    }

    private ManagerRequestFilter currentRequestFilter() {
        return new ManagerRequestFilter(
                search.getText(), statusFilter.getValue(), categoryFilter.getValue(),
                priorityFilter.getValue(), technicianFilter.getValue() == null
                        ? null : technicianFilter.getValue().id(),
                requestFrom.getValue(), requestThrough.getValue());
    }

    private AuditFilter currentAuditFilter() {
        return new AuditFilter(
                auditRequest.getText(), auditActor.getText(), auditAction.getText(),
                auditFrom.getValue(), auditThrough.getValue());
    }

    private void setBusy(boolean value) {
        busy = value;
        root.setDisable(value);
        updateActions();
        if (value) {
            feedback.setText("Working…");
        }
    }

    private void handleError(Throwable error) {
        setBusy(false);
        showError(UiTasks.safeMessage(error));
        failure.accept(error);
    }

    private void showError(String message) {
        feedback.getStyleClass().removeAll("feedback-success", "feedback-error");
        feedback.getStyleClass().add("feedback-error");
        feedback.setText(message);
    }

    private void showSuccess(String message) {
        feedback.getStyleClass().removeAll("feedback-success", "feedback-error");
        if (!message.isEmpty()) {
            feedback.getStyleClass().add("feedback-success");
        }
        feedback.setText(message);
    }

    private void restoreSelection(MaintenanceRequest previous) {
        if (previous != null) {
            requestTable.getItems().stream()
                    .filter(item -> item.id() == previous.id())
                    .findFirst()
                    .ifPresent(requestTable.getSelectionModel()::select);
        }
    }

    private Optional<UserAccount> selectedAccount() {
        UserAccount selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select an account first.");
        }
        return Optional.ofNullable(selected);
    }

    private MaintenanceRequest selectedRequest() {
        return Objects.requireNonNull(requestTable.getSelectionModel().getSelectedItem());
    }

    private static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        ok.setDefaultButton(false);
        cancel.setDefaultButton(true);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static void configureAction(
            Button button, String id, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        button.setMnemonicParsing(true);
        button.setId(id);
        button.setOnAction(action);
    }

    private static Parent padded(Parent content) {
        VBox wrapper = new VBox(content);
        wrapper.setPadding(new Insets(18));
        VBox.setVgrow(content, Priority.ALWAYS);
        return wrapper;
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Parent labeled(String text, javafx.scene.control.Control control) {
        Label label = new Label(text);
        label.setMnemonicParsing(true);
        label.setLabelFor(control);
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, label, control);
    }

    private static void addLabeled(
            GridPane pane, int column, String text, javafx.scene.control.Control control) {
        Label label = new Label(text);
        label.setMnemonicParsing(true);
        label.setLabelFor(control);
        pane.add(label, column, 0);
        pane.add(control, column, 1);
    }

    private static void addFormRow(
            GridPane pane, int row, String text, javafx.scene.control.Control control) {
        Label label = new Label(text);
        label.setLabelFor(control);
        pane.add(label, 0, row);
        pane.add(control, 1, row);
        control.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private static Tab fixedTab(String title, Parent content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static <T> TableColumn<T, String> textColumn(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setSortable(false);
        column.setPrefWidth(150);
        return column;
    }

    private static <T> javafx.collections.ObservableList<T> withNull(T[] values) {
        List<T> items = new ArrayList<>();
        items.add(null);
        items.addAll(List.of(values));
        return FXCollections.observableArrayList(items);
    }

    private static <T> javafx.collections.ObservableList<T> withNull(List<T> values) {
        List<T> items = new ArrayList<>();
        items.add(null);
        items.addAll(values);
        return FXCollections.observableArrayList(items);
    }

    private static String display(Enum<?> value) {
        String text = value.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String historyText(ManagerHistoryEntry entry) {
        return TIME.format(entry.occurredAt()) + " — " + entry.type() + " — "
                + entry.actor() + "\n" + entry.description();
    }

    private static String joinCounts(java.util.Map<? extends Enum<?>, Integer> counts) {
        return counts.entrySet().stream()
                .map(entry -> display(entry.getKey()) + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining("  |  "));
    }

    private record ManagerData(
            List<MaintenanceRequest> requests,
            List<UserAccount> technicians,
            List<UserAccount> requesters,
            ManagerDashboardSummary summary,
            List<UserAccount> accounts,
            List<AuditRecord> audit) {
    }
}
