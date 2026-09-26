package sg.edu.nus.facilityflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sg.edu.nus.facilityflow.auth.AuthFixture;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.ReportedUrgency;

class AuthenticatedWorkflowTest {
    @TempDir
    Path directory;
    private AuthFixture fixture;
    private ApplicationRouter router;
    private Stage stage;
    private final LinkedBlockingQueue<Runnable> work = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AuthFixture(directory);
        fx(() -> {
            router = new ApplicationRouter(
                    fixture.auth, fixture.requester, fixture.manager, fixture.managerAccounts,
                    fixture.technician, List.of("Plumbing"), new UiTasks(work::add));
            stage = new Stage();
            // Keep layout dimensions independent of native window limits and resize events.
            router.setManaged(false);
            router.resize(1024, 700);
            stage.setScene(new Scene(new javafx.scene.layout.Pane(router), 1024, 700));
            stage.getScene().getStylesheets().add(getClass()
                    .getResource("/sg/edu/nus/facilityflow/ui/facilityflow.css").toExternalForm());
            stage.show();
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        fixture.sessions.close();
        fx(() -> stage.close());
    }

    @ParameterizedTest
    @CsvSource({"owner, requesterDashboard", "manager, managerDashboard", "tech, technicianDashboard"})
    @DisplayName("AUT-014/015 UIX-001–004 each login reaches exactly its role view and logout removes it")
    void routesEachRole(String username, String expected) throws Exception {
        login(username);
        fx(() -> {
            assertNotNull(router.lookup("#" + expected));
            for (String role : List.of("requesterDashboard", "managerDashboard", "technicianDashboard")) {
                if (!role.equals(expected)) {
                    assertNull(router.lookup("#" + role));
                }
            }
            button("logout").fire();
            assertNotNull(router.lookup("#loginView"));
            assertNull(router.lookup("#" + expected));
        });
    }

    @Test
    @DisplayName("AUT-011/017 UIX-009 login masks and clears password and prevents pending repeated clicks")
    void rejectsWrongPasswordWithoutLeavingLogin() throws Exception {
        fx(() -> {
            ((TextField) router.lookup("#username")).setText("owner");
            ((PasswordField) router.lookup("#password")).setText("incorrect");
            button("login").fire();
            button("login").fire();
            assertEquals("", ((PasswordField) router.lookup("#password")).getText());
            assertTrue(button("login").isDisabled());
            assertEquals(1, work.size());
        });
        drain();
        fx(() -> assertEquals("Invalid username or password.", ((Label) router.lookup("#loginFeedback")).getText()));
    }

    @Test
    @DisplayName("REQ-002/003/011 UIX-009 create opens persisted detail and refreshes list with exactly one submission")
    void createsAndViewsRequest() throws Exception {
        login("owner");
        fillForm();
        fx(() -> {
            button("validate").fire();
            button("validate").fire();
            assertTrue(button("validate").isDisabled());
            assertTrue(button("logout").isDisabled());
            assertEquals(1, work.size());
        });
        drain();
        snapshot("requester-detail");
        fx(() -> {
            assertTrue(((Label) router.lookup("#requestDetail")).getText().contains("FF-000001"));
            assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("saved successfully"));
            button("backToRequests").fire();
        });
        drain();
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'REQUEST_CREATED'"));
        fx(() -> assertEquals(1, ((ListView<?>) router.lookup("#ownRequests")).getItems().size()));
        snapshot("requester-list");
    }

    @Test
    @DisplayName("REQ-001/004/005 UIX-014/015 dashboard shows own counts, applies filters, and resets them")
    void requesterDashboardFiltersAndResetsOwnRequests() throws Exception {
        var owner = fixture.auth.login("owner", "password".toCharArray());
        fixture.requester.createRequest(owner, requesterDraft("Leaking pipe", "Pantry sink"));
        var second = fixture.requester.createRequest(owner, requesterDraft("Broken light", "Room 12"));
        fixture.auth.logout(owner);
        var other = fixture.auth.login("other", "password".toCharArray());
        fixture.requester.createRequest(other, requesterDraft("Other person's leak", "Room 99"));
        fixture.auth.logout(other);
        var manager = fixture.auth.login("manager", "password".toCharArray());
        fixture.manager.assignOpenRequest(manager, second.id(), 4, ManagerPriority.HIGH);
        fixture.auth.logout(manager);

        login("owner");
        fx(() -> {
            assertEquals(2, ((ListView<?>) router.lookup("#ownRequests")).getItems().size());
            assertTrue(findRequesterSummary().getText().contains("Requests: 2 total"));
            assertTrue(findRequesterSummary().getText().contains("1 open"));
            assertTrue(findRequesterSummary().getText().contains("1 assigned"));

            ((TextField) router.lookup("#requesterSearch")).setText("PANTRY");
            buttonWithText("Apply filters").fire();
        });
        drain();
        fx(() -> assertEquals(1, ((ListView<?>) router.lookup("#ownRequests")).getItems().size()));

        fx(() -> {
            ((TextField) router.lookup("#requesterSearch")).clear();
            ((ComboBox<RequestStatus>) router.lookup("#requesterStatusFilter")).setValue(RequestStatus.ASSIGNED);
            ((ComboBox<String>) router.lookup("#requesterCategoryFilter")).setValue("Plumbing");
            LocalDate today = LocalDate.ofInstant(java.time.Instant.parse("2026-09-22T12:00:00Z"), ZoneId.systemDefault());
            ((DatePicker) router.lookup("#requesterFromDate")).setValue(today);
            ((DatePicker) router.lookup("#requesterThroughDate")).setValue(today);
            buttonWithText("Apply filters").fire();
        });
        drain();
        fx(() -> {
            var requests = (ListView<?>) router.lookup("#ownRequests");
            assertEquals(1, requests.getItems().size());
            assertEquals(second.id(), ((MaintenanceRequest) requests.getItems().getFirst()).id());
            buttonWithText("Reset filters").fire();
        });
        drain();
        fx(() -> {
            assertEquals(2, ((ListView<?>) router.lookup("#ownRequests")).getItems().size());
            assertEquals("", ((TextField) router.lookup("#requesterSearch")).getText());
            assertNull(((ComboBox<?>) router.lookup("#requesterStatusFilter")).getValue());
            assertNull(((ComboBox<?>) router.lookup("#requesterCategoryFilter")).getValue());
            assertNull(((DatePicker) router.lookup("#requesterFromDate")).getValue());
            assertNull(((DatePicker) router.lookup("#requesterThroughDate")).getValue());
        });
    }

    @Test
    @DisplayName("REQ-007/011/012 UIX-007/008/010 edit failure retains the draft and retry shows saved detail")
    void requesterEditFailureCanBeCorrectedAndRetried() throws Exception {
        login("owner");
        fillForm();
        fx(() -> button("validate").fire());
        drain();
        fx(() -> buttonWithText("Edit request").fire());
        fixture.execute("CREATE TRIGGER reject_request_edit BEFORE UPDATE ON maintenance_requests BEGIN SELECT RAISE(ABORT, 'injected error'); END");
        fx(() -> {
            ((TextField) router.lookup("#title")).setText("Updated pipe report");
            ((TextArea) router.lookup("#description")).setText("Water is still leaking under the pantry sink.");
            button("validate").fire();
        });
        drain();
        fx(() -> {
            assertEquals("Updated pipe report", ((TextField) router.lookup("#title")).getText());
            assertEquals("Water is still leaking under the pantry sink.", ((TextArea) router.lookup("#description")).getText());
            assertTrue(((Label) router.lookup("#feedback")).getText().contains("input has been kept"));
            assertFalse(((Label) router.lookup("#feedback")).getText().contains("injected error"));
            assertFalse(button("validate").isDisabled());
        });
        fixture.execute("DROP TRIGGER reject_request_edit");
        fx(() -> button("validate").fire());
        drain();
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests WHERE title='Updated pipe report'"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action='REQUEST_EDITED'"));
        fx(() -> {
            assertEquals("Updated pipe report", findLabel("Updated pipe report").getText());
            assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("Changes saved successfully"));
        });
    }

    @Test
    @DisplayName("REQ-008/012 UIX-011/012 cancellation dialog retains an invalid reason and retries")
    void requesterCancellationDialogCanRetryInvalidReason() throws Exception {
        login("owner");
        fillForm();
        fx(() -> button("validate").fire());
        drain();
        int[] responsesSent = {0};
        fx(() -> {
            respondToRequesterDialogs(List.of("bad", "No longer needed"), responsesSent);
            buttonWithText("Cancel request").fire();
        });
        drain();
        assertEquals(2, responsesSent[0]);
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests WHERE id=1 AND status='CANCELLED'"));
        fx(() -> {
            assertTrue(((Label) router.lookup("#requestDetail")).getText().contains("Status: CANCELLED"),
                    ((Label) router.lookup("#requestDetail")).getText());
            assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("Request cancelled"));
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action='REQUEST_CANCELLED' AND detail='No longer needed'"));
    }

    @Test
    @DisplayName("REQ-009/011/012 UIX-008/010/012 follow-up failure retains text and retry refreshes visible history")
    void requesterFollowUpFailureCanBeRetried() throws Exception {
        login("owner");
        fillForm();
        fx(() -> button("validate").fire());
        drain();
        fixture.execute("CREATE TRIGGER reject_requester_update BEFORE INSERT ON requester_updates BEGIN SELECT RAISE(ABORT, 'injected error'); END");
        fx(() -> {
            TextArea update = requesterFollowUpField();
            update.setText("Please call before entering the room.");
            buttonWithText("Add follow-up").fire();
        });
        drain();
        fx(() -> {
            assertEquals("Please call before entering the room.", requesterFollowUpField().getText());
            assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("input has been kept"));
            assertFalse(((Label) router.lookup("#requesterFeedback")).getText().contains("injected error"));
        });
        fixture.execute("DROP TRIGGER reject_requester_update");
        fx(() -> buttonWithText("Add follow-up").fire());
        drain();
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM requester_updates WHERE text='Please call before entering the room.'"));
        fx(() -> {
            assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("Follow-up saved successfully"));
            assertTrue(router.lookup(".scroll-pane").lookupAll(".label").stream()
                    .map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Please call before entering the room.")));
        });
    }

    @Test
    @DisplayName("REQ-A08 AUT-014 MGR-005 UI handoff creates, assigns and shows persisted status to the owner only")
    void handsOffAcrossAuthenticatedViews() throws Exception {
        login("owner");
        fillForm();
        fx(() -> button("validate").fire());
        drain();
        fx(() -> button("logout").fire());
        login("manager");
        fx(() -> {
            ((TableView<?>) router.lookup("#managerRequests")).getSelectionModel().selectFirst();
            ((ComboBox<?>) router.lookup("#assignee")).getSelectionModel().selectFirst();
            ((ComboBox<?>) router.lookup("#managerPriority")).getSelectionModel().selectFirst();
            button("assignRequest").fire();
        });
        drain();
        fx(() -> button("logout").fire());
        login("other");
        fx(() -> {
            assertTrue(((ListView<?>) router.lookup("#ownRequests")).getItems().isEmpty());
            button("logout").fire();
        });
        login("owner");
        fx(() -> {
            ((ListView<?>) router.lookup("#ownRequests")).getSelectionModel().selectFirst();
            button("viewRequest").fire();
        });
        drain();
        fx(() -> assertTrue(((Label) router.lookup("#requestDetail")).getText().contains("Status: ASSIGNED")));
    }

    @Test
    @DisplayName("MGR-001/007 QLT-005 Manager reviews completed work and closes it from the dashboard")
    void managerClosesCompletedWorkFromDashboard() throws Exception {
        seedTechnicianQueue();
        login("manager");

        fx(() -> {
            var table = (TableView<MaintenanceRequest>) router.lookup("#managerRequests");
            MaintenanceRequest completed = table.getItems().stream()
                    .filter(request -> request.status() == RequestStatus.COMPLETED)
                    .findFirst()
                    .orElseThrow();
            table.getSelectionModel().select(completed);
        });
        drain();
        fx(() -> {
            assertFalse(button("closeRequest").isDisabled());
            confirmNextDialog();
            button("closeRequest").fire();
        });
        drain();

        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests "
                + "WHERE id=3 AND status='CLOSED' AND resolution_summary IS NOT NULL"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events "
                + "WHERE request_id=3 AND actor_id=3 AND action='REQUEST_CLOSED'"));
        fx(() -> {
            var table = (TableView<MaintenanceRequest>) router.lookup("#managerRequests");
            MaintenanceRequest closed = table.getItems().stream()
                    .filter(request -> request.id() == 3)
                    .findFirst()
                    .orElseThrow();
            assertEquals(RequestStatus.CLOSED, closed.status());
            assertTrue(((Label) router.lookup("#managerFeedback")).getText()
                    .contains("Completed work closed"));
        });
    }

    @Test
    @DisplayName("TEC-001/004/013 TEC-A01 LIF-011/012 starts assigned work and refreshes persisted UI state")
    void technicianStartsAssignedWorkFromQueue() throws Exception {
        login("owner");
        fillForm();
        fx(() -> button("validate").fire());
        drain();

        fx(() -> button("logout").fire());
        login("manager");
        fx(() -> {
            ((TableView<?>) router.lookup("#managerRequests")).getSelectionModel().selectFirst();
            ((ComboBox<?>) router.lookup("#assignee")).getSelectionModel().selectFirst();
            ((ComboBox<?>) router.lookup("#managerPriority")).getSelectionModel().selectFirst();
            button("assignRequest").fire();
        });
        drain();

        fx(() -> button("logout").fire());
        login("tech");
        fx(() -> {
            var table = (TableView<?>) router.lookup("#technicianRequests");
            assertEquals(1, table.getItems().size());
            assertEquals(RequestStatus.ASSIGNED, ((MaintenanceRequest) table.getItems().getFirst()).status());
            assertTrue(button("startWork").isDisabled());

            table.getSelectionModel().selectFirst();
            assertFalse(button("startWork").isDisabled());
            button("startWork").fire();
            assertTrue(button("startWork").isDisabled());
            assertEquals("Starting work…", ((Label) router.lookup("#technicianFeedback")).getText());
        });
        drain();

        fx(() -> {
            var table = (TableView<?>) router.lookup("#technicianRequests");
            assertEquals(1, table.getItems().size());
            assertEquals(RequestStatus.IN_PROGRESS, ((MaintenanceRequest) table.getItems().getFirst()).status());
            assertTrue(button("startWork").isDisabled());
            assertEquals("FF-000001 is now IN_PROGRESS. Work started successfully.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
            assertTrue(((Label) router.lookup("#technicianRequestDetails")).getText()
                    .contains("Status: In progress"));
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests "
                + "WHERE display_id = 'FF-000001' AND status = 'IN_PROGRESS' AND assignee_id = 4"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events "
                + "WHERE request_id = 1 AND actor_id = 4 AND action = 'REQUEST_STARTED'"));
    }

    @Test
    @DisplayName("TEC-001/002 UIX-010/013 dashboard shows current queue counts and deterministic route wiring")
    void technicianDashboardShowsCountsAndWiresConfiguredCategories() throws Exception {
        seedTechnicianQueue();
        login("tech");

        fx(() -> {
            assertEquals("Assigned: 1", ((Label) router.lookup("#technicianAssignedCount")).getText());
            assertEquals("In progress: 1", ((Label) router.lookup("#technicianInProgressCount")).getText());
            assertEquals("Awaiting review: 1", ((Label) router.lookup("#technicianCompletedCount")).getText());

            var categories = (ComboBox<?>) router.lookup("#technicianCategoryFilter");
            assertEquals(2, categories.getItems().size());
            assertNull(categories.getItems().get(0));
            assertEquals("Plumbing", categories.getItems().get(1));
            assertNotNull(router.lookup("#technicianFilterBar"));
            assertEquals("Technician assigned requests",
                    ((TableView<?>) router.lookup("#technicianRequests")).getAccessibleText());
        });
    }

    @ParameterizedTest
    @CsvSource({"bOiLeR, Boiler leak", "nOrTh block, Boiler leak", "ff-000002, Lift alarm"})
    @DisplayName("TEC-002 LIF-018 case-insensitive Technician search matches ID, title, and location")
    void technicianSearchMatchesIdTitleAndLocationIgnoringCase(String query, String expectedTitle)
            throws Exception {
        seedTechnicianQueue();
        login("tech");

        fx(() -> {
            ((TextField) router.lookup("#technicianSearch")).setText(query);
            button("applyTechnicianFilters").fire();
        });
        drain();

        fx(() -> {
            var table = (TableView<?>) router.lookup("#technicianRequests");
            assertEquals(1, table.getItems().size());
            assertEquals(expectedTitle, ((MaintenanceRequest) table.getItems().getFirst()).title());
        });
    }

    @Test
    @DisplayName("TEC-002 LIF-017 status/category/priority filters combine and reset in one action")
    void technicianFiltersApplyAndReset() throws Exception {
        seedTechnicianQueue();
        login("tech");

        fx(() -> {
            ((ComboBox<RequestStatus>) router.lookup("#technicianStatusFilter"))
                    .getSelectionModel().select(RequestStatus.COMPLETED);
            ((ComboBox<String>) router.lookup("#technicianCategoryFilter"))
                    .getSelectionModel().select("Plumbing");
            ((ComboBox<ManagerPriority>) router.lookup("#technicianPriorityFilter"))
                    .getSelectionModel().select(ManagerPriority.HIGH);
            button("applyTechnicianFilters").fire();
        });
        drain();

        fx(() -> {
            var table = (TableView<?>) router.lookup("#technicianRequests");
            assertEquals(0, table.getItems().size(), "all three criteria must be applied together");
            assertEquals("No requests match the current search and filters.",
                    ((Label) table.getPlaceholder()).getText());

            ((ComboBox<RequestStatus>) router.lookup("#technicianStatusFilter"))
                    .getSelectionModel().select(RequestStatus.IN_PROGRESS);
            ((ComboBox<String>) router.lookup("#technicianCategoryFilter"))
                    .getSelectionModel().select("Plumbing");
            ((ComboBox<ManagerPriority>) router.lookup("#technicianPriorityFilter"))
                    .getSelectionModel().select(ManagerPriority.HIGH);
            button("applyTechnicianFilters").fire();
        });
        drain();

        fx(() -> {
            var table = (TableView<?>) router.lookup("#technicianRequests");
            assertEquals(1, table.getItems().size());
            assertEquals("Lift alarm", ((MaintenanceRequest) table.getItems().getFirst()).title());

            button("resetTechnicianFilters").fire();
            assertEquals("", ((TextField) router.lookup("#technicianSearch")).getText());
            assertNull(((ComboBox<?>) router.lookup("#technicianStatusFilter")).getValue());
            assertNull(((ComboBox<?>) router.lookup("#technicianCategoryFilter")).getValue());
            assertNull(((ComboBox<?>) router.lookup("#technicianPriorityFilter")).getValue());
        });
        drain();

        fx(() -> assertEquals(3,
                ((TableView<?>) router.lookup("#technicianRequests")).getItems().size()));
    }

    @Test
    @DisplayName("TEC-002/003 LIF-019/020 empty filtered results remain valid and deterministic")
    void technicianEmptyFilterResultHasDedicatedPlaceholder() throws Exception {
        seedTechnicianQueue();
        login("tech");

        fx(() -> {
            ((TextField) router.lookup("#technicianSearch")).setText("does-not-exist");
            button("applyTechnicianFilters").fire();
        });
        drain();

        fx(() -> {
            var table = (TableView<?>) router.lookup("#technicianRequests");
            assertTrue(table.getItems().isEmpty());
            assertEquals("No requests match the current search and filters.",
                    ((Label) table.getPlaceholder()).getText());
            assertEquals("does-not-exist", ((TextField) router.lookup("#technicianSearch")).getText());
        });
    }

    @ParameterizedTest
    @CsvSource({"760, 600", "1024, 700", "1440, 900"})
    @DisplayName("UIX-006/013/015/017/018/021/023 Technician layout reflows and labels controls")
    void responsiveTechnician(int width, int height) throws Exception {
        seedTechnicianQueue();
        login("tech");
        resize(width, height);

        fx(() -> {
            var layout = (javafx.scene.control.SplitPane) router.lookup("#technicianLayout");
            assertEquals(width < 1100 ? Orientation.VERTICAL : Orientation.HORIZONTAL,
                    layout.getOrientation());
            assertEquals("Search ID, title, or location",
                    ((TextField) router.lookup("#technicianSearch")).getPromptText());
            assertSame(router.lookup("#technicianSearch"), findLabel("_Search").getLabelFor());
            assertSame(router.lookup("#technicianStatusFilter"), findLabel("_Status").getLabelFor());
            assertSame(router.lookup("#technicianCategoryFilter"), findLabel("_Category").getLabelFor());
            assertSame(router.lookup("#technicianPriorityFilter"), findLabel("_Priority").getLabelFor());
            assertSame(router.lookup("#technicianWorkLogNote"),
                    findLabel("_Work performed (required)").getLabelFor());
            assertSame(router.lookup("#technicianWorkLogMinutes"),
                    findLabel("_Minutes spent (required)").getLabelFor());
            assertSame(router.lookup("#technicianResolutionSummary"),
                    findLabel("_Resolution summary (required)").getLabelFor());
            assertTrue(button("startWork").isMnemonicParsing());
            assertTrue(button("addWorkLog").isMnemonicParsing());
            assertInsideScene(button("refreshTechnicianRequests"));

            var detailScroll = (javafx.scene.control.ScrollPane) layout.getItems().get(1);
            detailScroll.setVvalue(1);
            router.layout();
            assertInsideScene(button("completeWork"));
        });
    }

    @Test
    @DisplayName("TEC-004–007/013 LIF-007/008/010–012 authenticated Technician UI logs work, shows history, and preserves invalid input")
    void technicianAddsVisibleWorkLogWithUiGuards() throws Exception {
        loginTechnicianWithAssignedRequest();

        fx(() -> {
            var table = (TableView<?>) router.lookup("#technicianRequests");
            table.getSelectionModel().selectFirst();
        });
        drain();

        fx(() -> {
            assertTrue(button("addWorkLog").isDisabled());
            button("startWork").fire();
            button("startWork").fire();
            assertTrue(button("startWork").isDisabled(), "the second click must be ignored while busy");
            assertEquals(1, work.size(), "duplicate start clicks must enqueue one write");
        });
        drain();
        fx(() -> { });

        fx(() -> {
            var note = (TextArea) router.lookup("#technicianWorkLogNote");
            var minutes = (TextField) router.lookup("#technicianWorkLogMinutes");
            note.setText("Inspected the leaking pipe and replaced the washer.");
            minutes.setText("45");
            button("addWorkLog").fire();
            button("addWorkLog").fire();
            assertTrue(button("addWorkLog").isDisabled(), "the second click must be ignored while busy");
            assertEquals(1, work.size(), "duplicate work-log clicks must enqueue one write");
        });
        drain();

        fx(() -> {
            var history = (ListView<?>) router.lookup("#technicianWorkLogs");
            assertEquals(1, history.getItems().size());
            assertTrue(history.getItems().getFirst().toString().contains("45 minutes"));
            assertTrue(history.getItems().getFirst().toString()
                    .contains("Inspected the leaking pipe and replaced the washer."));
            assertEquals("FF-000001 work log saved successfully.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
            assertEquals("", ((TextArea) router.lookup("#technicianWorkLogNote")).getText());
            assertEquals("", ((TextField) router.lookup("#technicianWorkLogMinutes")).getText());
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = 1"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM work_logs "
                + "WHERE request_id = 1 AND author_id = 4 AND minutes_spent = 45"));

        fx(() -> {
            var note = (TextArea) router.lookup("#technicianWorkLogNote");
            var minutes = (TextField) router.lookup("#technicianWorkLogMinutes");
            note.setText("  Keep this invalid entry visible  ");
            minutes.setText("not-a-number");
            button("addWorkLog").fire();
            assertEquals("  Keep this invalid entry visible  ", note.getText());
            assertEquals("not-a-number", minutes.getText());
            assertEquals("Minutes spent must be a whole number from 1 to 1,440.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = 1"));

        fx(() -> {
            ((TextField) router.lookup("#technicianWorkLogMinutes")).setText("1441");
            button("addWorkLog").fire();
        });
        drain();
        fx(() -> {
            assertEquals("  Keep this invalid entry visible  ",
                    ((TextArea) router.lookup("#technicianWorkLogNote")).getText());
            assertEquals("1441", ((TextField) router.lookup("#technicianWorkLogMinutes")).getText());
            assertEquals("Minutes spent must be from 1 to 1,440.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
            assertEquals(1, ((ListView<?>) router.lookup("#technicianWorkLogs")).getItems().size());
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = 1"));
    }

    @Test
    @DisplayName("TEC-008/009/013 TEC-A05 LIF-005/007/011/012 completes logged work once and preserves assignment")
    void technicianCompletesLoggedWorkForManagerReview() throws Exception {
        loginTechnicianWithAssignedRequest();

        fx(() -> ((TableView<?>) router.lookup("#technicianRequests"))
                .getSelectionModel().selectFirst());
        drain();
        fx(() -> button("startWork").fire());
        drain();
        fx(() -> { });

        fx(() -> {
            ((TextArea) router.lookup("#technicianWorkLogNote"))
                    .setText("Replaced the damaged valve and tested the fitting.");
            ((TextField) router.lookup("#technicianWorkLogMinutes")).setText("45");
            button("addWorkLog").fire();
        });
        drain();

        fx(() -> {
            assertFalse(button("completeWork").isDisabled());
            ((TextArea) router.lookup("#technicianResolutionSummary"))
                    .setText("  Replaced valve and verified normal water flow.  ");
            button("completeWork").fire();
            button("completeWork").fire();
            assertTrue(button("completeWork").isDisabled(),
                    "duplicate completion clicks must be ignored while busy");
            assertEquals(1, work.size(), "duplicate completion clicks must enqueue one write");
        });
        drain();

        fx(() -> {
            MaintenanceRequest completed = (MaintenanceRequest) ((TableView<?>)
                    router.lookup("#technicianRequests")).getItems().getFirst();
            assertEquals(RequestStatus.COMPLETED, completed.status());
            assertEquals(4L, completed.assigneeId());
            assertTrue(button("completeWork").isDisabled());
            assertEquals("", ((TextArea) router.lookup("#technicianResolutionSummary")).getText());
            assertEquals("FF-000001 was submitted for Manager review successfully.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests "
                + "WHERE id = 1 AND status = 'COMPLETED' AND assignee_id = 4 "
                + "AND resolution_summary = 'Replaced valve and verified normal water flow.'"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM work_logs "
                + "WHERE request_id = 1 AND author_id = 4"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events "
                + "WHERE request_id = 1 AND actor_id = 4 AND action = 'REQUEST_COMPLETED'"));
    }

    @Test
    @DisplayName("TEC-008 TEC-A04 LIF-005/007 rejects missing evidence and invalid summary while retaining input")
    void rejectsCompletionWithoutEvidenceOrValidSummaryInUi() throws Exception {
        loginTechnicianWithAssignedRequest();

        fx(() -> ((TableView<?>) router.lookup("#technicianRequests"))
                .getSelectionModel().selectFirst());
        drain();
        fx(() -> button("startWork").fire());
        drain();
        fx(() -> {
            assertTrue(button("completeWork").isDisabled(),
                    "completion must remain unavailable until work evidence is loaded");
            assertEquals(RequestStatus.IN_PROGRESS, ((MaintenanceRequest) ((TableView<?>)
                    router.lookup("#technicianRequests")).getItems().getFirst()).status());
        });

        fx(() -> {
            ((TextArea) router.lookup("#technicianWorkLogNote"))
                    .setText("Completed a careful inspection of the valve.");
            ((TextField) router.lookup("#technicianWorkLogMinutes")).setText("20");
            button("addWorkLog").fire();
        });
        drain();

        fx(() -> {
            TextArea summary = (TextArea) router.lookup("#technicianResolutionSummary");
            summary.setText("Too short");
            button("completeWork").fire();
            assertTrue(button("completeWork").isDisabled());
        });
        drain();

        fx(() -> {
            assertEquals("Too short", ((TextArea) router.lookup("#technicianResolutionSummary"))
                    .getText());
            assertEquals("Resolution summary must contain 10 to 2,000 characters.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
            assertEquals(RequestStatus.IN_PROGRESS, ((MaintenanceRequest) ((TableView<?>)
                    router.lookup("#technicianRequests")).getItems().getFirst()).status());
        });
        assertEquals(0, fixture.scalar("SELECT COUNT(*) FROM audit_events "
                + "WHERE request_id = 1 AND action = 'REQUEST_COMPLETED'"));
    }

    @Test
    @DisplayName("TEC-012/013 LIF-011/016 stale completion is rejected and refreshes the queue")
    void staleTechnicianCompletionRefreshesQueueAfterReassignment() throws Exception {
        loginTechnicianWithAssignedRequest();
        addSecondTechnician();

        fx(() -> ((TableView<?>) router.lookup("#technicianRequests"))
                .getSelectionModel().selectFirst());
        drain();
        fx(() -> button("startWork").fire());
        drain();
        fx(() -> { });
        fx(() -> {
            ((TextArea) router.lookup("#technicianWorkLogNote"))
                    .setText("Recorded the repair and verified the final condition.");
            ((TextField) router.lookup("#technicianWorkLogMinutes")).setText("30");
            button("addWorkLog").fire();
        });
        drain();

        var managerSession = fixture.auth.login("manager", "password".toCharArray());
        fixture.manager.reassignRequest(managerSession, 1, 6, "Coverage handoff");
        fixture.auth.logout(managerSession);

        fx(() -> {
            ((TextArea) router.lookup("#technicianResolutionSummary"))
                    .setText("Repair completed and tested successfully.");
            button("completeWork").fire();
            assertTrue(button("completeWork").isDisabled());
            assertEquals(1, work.size());
        });
        drain();

        fx(() -> {
            assertTrue(((TableView<?>) router.lookup("#technicianRequests")).getItems().isEmpty());
            assertEquals("The request assignment or status changed. The queue was refreshed.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
        });
        assertEquals(6, fixture.scalar("SELECT assignee_id FROM maintenance_requests WHERE id = 1"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = 1"));
        assertEquals(0, fixture.scalar("SELECT COUNT(*) FROM audit_events "
                + "WHERE request_id = 1 AND action = 'REQUEST_COMPLETED'"));
    }

    @Test
    @DisplayName("TEC-012/013 LIF-011/016 stale Technician work-log write refreshes the authenticated queue")
    void staleTechnicianWriteRefreshesQueueAfterReassignment() throws Exception {
        loginTechnicianWithAssignedRequest();
        addSecondTechnician();

        fx(() -> ((TableView<?>) router.lookup("#technicianRequests"))
                .getSelectionModel().selectFirst());
        drain();
        fx(() -> button("startWork").fire());
        drain();
        fx(() -> { });

        var managerSession = fixture.auth.login("manager", "password".toCharArray());
        fixture.manager.reassignRequest(managerSession, 1, 6, "Coverage handoff");
        fixture.auth.logout(managerSession);

        fx(() -> {
            ((TextArea) router.lookup("#technicianWorkLogNote")).setText("Stale update");
            ((TextField) router.lookup("#technicianWorkLogMinutes")).setText("10");
            button("addWorkLog").fire();
            assertTrue(button("addWorkLog").isDisabled());
            assertEquals(1, work.size());
        });
        drain();

        fx(() -> {
            assertTrue(((TableView<?>) router.lookup("#technicianRequests")).getItems().isEmpty());
            assertEquals("The request assignment or status changed. The queue was refreshed.",
                    ((Label) router.lookup("#technicianFeedback")).getText());
        });
        assertEquals(0, fixture.scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = 1"));
        assertEquals(6, fixture.scalar("SELECT assignee_id FROM maintenance_requests WHERE id = 1"));
    }

    @Test
    @DisplayName("REQ-012 UIX-008/012 failed save preserves raw input, reports safe failure, and permits one retry")
    void preservesInputOnFailure() throws Exception {
        login("owner");
        fillForm();
        fixture.execute("CREATE TRIGGER reject_audit BEFORE INSERT ON audit_events BEGIN SELECT RAISE(ABORT, 'private error'); END");
        fx(() -> button("validate").fire());
        drain();
        fx(() -> {
            assertEquals("  Leaking pipe  ", ((TextField) router.lookup("#title")).getText());
            String message = ((Label) router.lookup("#feedback")).getText();
            assertTrue(message.contains("input has been kept"));
            assertFalse(message.contains("private error"));
            assertFalse(button("validate").isDisabled());
        });
        assertEquals(0, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests"));
        fixture.execute("DROP TRIGGER reject_audit");
        fx(() -> button("validate").fire());
        drain();
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests"));
    }

    @Test
    @DisplayName("AUT-032 reroutes a live role change without login and removes old-role navigation")
    void reroutesChangedRole() throws Exception {
        login("owner");
        fixture.execute("UPDATE user_accounts SET role = 'TECHNICIAN' WHERE id = 1");
        fx(() -> button("refreshAccount").fire());
        drain();
        fx(() -> {
            assertNotNull(router.lookup("#technicianDashboard"));
            assertNull(router.lookup("#requesterDashboard"));
            assertNull(router.lookup("#newRequest"));
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'LOGIN_SUCCEEDED'"));
    }

    @Test
    @DisplayName("AUT-022 REQ-012 expired-session save returns to login and retains same-owner draft after reauthentication")
    void restoresDraftAfterExpiredSession() throws Exception {
        login("owner");
        fillForm();
        fixture.execute("UPDATE user_accounts SET active = 0 WHERE id = 1");
        fx(() -> button("validate").fire());
        drain();
        fx(() -> assertNotNull(router.lookup("#loginView")));
        fixture.execute("UPDATE user_accounts SET active = 1 WHERE id = 1");
        login("owner");
        fx(() -> {
            assertEquals("  Leaking pipe  ", ((TextField) router.lookup("#title")).getText());
            button("validate").fire();
        });
        drain();
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests"));
    }

    private void seedTechnicianQueue() throws Exception {
        fixture.execute("""
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id,
                    created_at, updated_at, assigned_at, resolution_summary, completed_at)
                VALUES (1, 'FF-000001', 1, 'Boiler leak',
                    'Water dripping from the boiler connection.', 'North Block', 'Plumbing',
                    'HIGH', 'CRITICAL', 'ASSIGNED', 4,
                    '2026-09-19T08:00:00Z', '2026-09-20T08:00:00Z', '2026-09-20T08:00:00Z', NULL, NULL)
                """);
        fixture.execute("""
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id,
                    created_at, updated_at, assigned_at, resolution_summary, completed_at)
                VALUES (2, 'FF-000002', 1, 'Lift alarm',
                    'The lift alarm sounds in the east tower.', 'East Tower', 'Plumbing',
                    'EMERGENCY', 'HIGH', 'IN_PROGRESS', 4,
                    '2026-09-18T08:00:00Z', '2026-09-21T08:00:00Z', '2026-09-19T10:00:00Z', NULL, NULL)
                """);
        fixture.execute("""
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id,
                    created_at, updated_at, assigned_at, resolution_summary, completed_at)
                VALUES (3, 'FF-000003', 1, 'Window hinge',
                    'The window hinge is loose in the south wing.', 'South Wing', 'Plumbing',
                    'LOW', 'LOW', 'COMPLETED', 4,
                    '2026-09-17T08:00:00Z', '2026-09-21T09:00:00Z', '2026-09-18T10:00:00Z',
                    'Hinge tightened and window tested safely.', '2026-09-21T09:00:00Z')
                """);
    }

    private Label findLabel(String text) {
        Label label = findLabel(router, text);
        assertNotNull(label, "Missing label: " + text);
        return label;
    }

    private Label findLabel(Parent parent, String text) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Label label && text.equals(label.getText())) {
                return label;
            }
            if (child instanceof Parent nested) {
                Label label = findLabel(nested, text);
                if (label != null) {
                    return label;
                }
            }
        }
        return null;
    }

    private void loginTechnicianWithAssignedRequest() throws Exception {
        login("owner");
        fillForm();
        fx(() -> button("validate").fire());
        drain();

        fx(() -> button("logout").fire());
        login("manager");
        fx(() -> {
            ((TableView<?>) router.lookup("#managerRequests")).getSelectionModel().selectFirst();
            ((ComboBox<?>) router.lookup("#assignee")).getSelectionModel().selectFirst();
            ((ComboBox<?>) router.lookup("#managerPriority")).getSelectionModel().selectFirst();
            button("assignRequest").fire();
        });
        drain();

        fx(() -> button("logout").fire());
        login("tech");
    }

    private void addSecondTechnician() throws Exception {
        fixture.execute("""
                INSERT INTO user_accounts(
                    id, username, display_name, role, password_hash, active, created_at, updated_at)
                VALUES (6, 'tech-two', 'Technician Two', 'TECHNICIAN', 'unused', 1,
                    '2026-09-22T00:00:00Z', '2026-09-22T00:00:00Z')
                """);
    }

    private void login(String username) throws Exception {
        fx(() -> {
            ((TextField) router.lookup("#username")).setText(username);
            ((PasswordField) router.lookup("#password")).setText("password");
            button("login").fire();
        });
        drain();
    }

    @ParameterizedTest
    @CsvSource({"760, 600", "1024, 700", "1440, 900"})
    @DisplayName("UIX-003/007/008/021 resizing preserves input and keeps actions reachable")
    void responsiveRequester(int width, int height) throws Exception {
        resize(width, height);
        fx(() -> assertInsideScene(button("login")));
        snapshot("login-" + width);
        login("owner");
        fx(() -> {
            assertInsideScene(button("logout"));
            assertInsideScene(button("newRequest"));
        });
        fillForm();
        resize(width == 760 ? 1440 : 760, height);
        fx(() -> assertEquals("  Leaking pipe  ", ((TextField) router.lookup("#title")).getText()));
        resize(width, height);
        fx(() -> {
            ((TextField) router.lookup("#title")).clear();
            button("validate").fire();
        });
        fx(() -> {
            assertTrue(router.lookup("#titleError").isVisible());
            var scroll = (javafx.scene.control.ScrollPane) router.lookup(".scroll-pane");
            assertTrue(scroll.getContent().getBoundsInLocal().getWidth() <= scroll.getViewportBounds().getWidth() + 1);
            scroll.setVvalue(1);
            router.layout();
            assertInsideScene(button("validate"));
        });
        snapshot("requester-form-" + width);
    }

    @ParameterizedTest
    @CsvSource({"760, 600", "1024, 700", "1440, 900"})
    @DisplayName("UIX-021 Manager panels reflow without losing assignment selections")
    void responsiveManager(int width, int height) throws Exception {
        login("manager");
        fx(() -> ((ComboBox<?>) router.lookup("#assignee")).getSelectionModel().selectFirst());
        resize(width, height);
        fx(() -> {
            assertInsideScene(button("logout"));
            var layout = (javafx.scene.control.SplitPane) router.lookup("#managerLayout");
            assertEquals(width < 1100 ? javafx.geometry.Orientation.VERTICAL : javafx.geometry.Orientation.HORIZONTAL,
                    layout.getOrientation());
            assertNotNull(((ComboBox<?>) router.lookup("#assignee")).getValue());
            var scroll = (javafx.scene.control.ScrollPane) layout.getItems().get(1);
            scroll.setVvalue(1);
            router.layout();
            assertInsideScene(button("assignRequest"));
        });
        snapshot("manager-" + width);
    }

    // Optional review artifacts use fixture data only; normal test runs write no screenshots.
    private void snapshot(String name) throws Exception {
        String output = System.getenv("FACILITYFLOW_UI_SNAPSHOTS");
        if (output == null || output.isBlank()) {
            return;
        }
        fx(() -> {
            var image = router.snapshot(null, null);
            var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < pixels.getHeight(); y++) {
                for (int x = 0; x < pixels.getWidth(); x++) {
                    pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            try {
                java.nio.file.Files.createDirectories(Path.of(output));
                javax.imageio.ImageIO.write(pixels, "png", Path.of(output, name + ".png").toFile());
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        });
    }

    private void resize(int width, int height) throws Exception {
        fx(() -> {
            router.resize(width, height);
            router.layout();
            assertEquals(width, router.getWidth());
            assertEquals(height, router.getHeight());
        });
        fx(() -> { });
    }

    private void assertInsideScene(javafx.scene.Node node) {
        var bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= router.getWidth() + 1,
                node.getId() + " must fit horizontally");
        assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= router.getHeight() + 1,
                node.getId() + " must fit vertically");
    }

    private void fillForm() throws Exception {
        fx(() -> button("newRequest").fire());
        fx(() -> {
            ((TextField) router.lookup("#title")).setText("  Leaking pipe  ");
            ((TextArea) router.lookup("#description")).setText("Water leaks under the sink.");
            ((TextField) router.lookup("#location")).setText("Room 12");
            ((ComboBox<?>) router.lookup("#category")).getSelectionModel().selectFirst();
            ((ComboBox<?>) router.lookup("#urgency")).getSelectionModel().selectFirst();
        });
    }

    private Button button(String id) {
        return (Button) router.lookup("#" + id);
    }

    private Button buttonWithText(String text) {
        return router.lookupAll(".button").stream().map(Button.class::cast)
                .filter(candidate -> text.equals(candidate.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing button: " + text));
    }

    private Label findRequesterSummary() {
        return router.lookup("#requesterDashboard").lookupAll(".label").stream()
                .map(Label.class::cast).filter(label -> label.getText() != null
                        && label.getText().startsWith("Requests:"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing requester request summary"));
    }

    private TextArea requesterFollowUpField() {
        return router.lookupAll(".text-area").stream().map(TextArea.class::cast)
                .filter(area -> "Add a follow-up update".equals(area.getPromptText())).findFirst()
                .orElseThrow(() -> new AssertionError("Missing follow-up input"));
    }

    private void respondToRequesterDialogs(List<String> responses, int[] responsesSent) {
        int[] responseIndex = {0};
        Runnable[] responder = new Runnable[1];
        responder[0] = () -> {
            if (responseIndex[0] >= responses.size()) {
                return;
            }
            Window dialog = Window.getWindows().stream().filter(Window::isShowing)
                    .filter(window -> window != stage).findFirst().orElse(null);
            if (dialog == null || !(dialog.getScene().getRoot() instanceof DialogPane pane)) {
                var pause = new PauseTransition(Duration.millis(50));
                pause.setOnFinished(event -> responder[0].run());
                pause.playFromStart();
                return;
            }
            if (responseIndex[0] > 0) {
                assertTrue(containsDialogText(pane.getContent(), "Cancellation reason must contain 5 to 500 characters."),
                        "the invalid cancellation reason should receive field-specific correction guidance");
            }
            dialogTextField(pane).setText(responses.get(responseIndex[0]));
            responseIndex[0]++;
            responsesSent[0] = responseIndex[0];
            ((Button) pane.lookupButton(ButtonType.OK)).fire();
            if (responseIndex[0] < responses.size()) {
                var pause = new PauseTransition(Duration.millis(50));
                pause.setOnFinished(event -> responder[0].run());
                pause.playFromStart();
            }
        };
        Platform.runLater(responder[0]);
    }

    private void confirmNextDialog() {
        Runnable[] responder = new Runnable[1];
        responder[0] = () -> {
            Window dialog = Window.getWindows().stream().filter(Window::isShowing)
                    .filter(window -> window != stage).findFirst().orElse(null);
            if (dialog == null || !(dialog.getScene().getRoot() instanceof DialogPane pane)) {
                var pause = new PauseTransition(Duration.millis(50));
                pause.setOnFinished(event -> responder[0].run());
                pause.playFromStart();
                return;
            }
            ((Button) pane.lookupButton(ButtonType.OK)).fire();
        };
        Platform.runLater(responder[0]);
    }

    private TextField dialogTextField(DialogPane pane) {
        TextField field = findTextField(pane.getContent());
        if (field == null) {
            throw new AssertionError("Requester cancellation dialog has no text input");
        }
        return field;
    }

    private boolean containsDialogText(Node node, String expected) {
        if (node instanceof Label label && expected.equals(label.getText())) {
            return true;
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsDialogText(child, expected));
        }
        return false;
    }

    private TextField findTextField(Node node) {
        if (node instanceof TextField field) {
            return field;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                TextField field = findTextField(child);
                if (field != null) {
                    return field;
                }
            }
        }
        return null;
    }

    private static RequestDraft requesterDraft(String title, String location) {
        return new RequestDraft(title, "Please inspect the reported maintenance issue.", location,
                "Plumbing", ReportedUrgency.NORMAL);
    }

    private void drain() throws Exception {
        Runnable task;
        while ((task = work.poll()) != null) {
            assertFalse(Platform.isFxApplicationThread());
            task.run();
            fx(() -> { });
        }
    }

    private void fx(Runnable action) throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            if (router != null) {
                router.applyCss();
                router.layout();
            }
            action.run();
        });
    }
}
