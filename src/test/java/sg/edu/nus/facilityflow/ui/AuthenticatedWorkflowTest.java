package sg.edu.nus.facilityflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sg.edu.nus.facilityflow.auth.AuthFixture;

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
            router = new ApplicationRouter(fixture.auth, fixture.requester, fixture.manager,
                    List.of("Plumbing"), new UiTasks(work::add));
            stage = new Stage();
            stage.setScene(new Scene(router, 1024, 700));
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
        fx(() -> {
            assertTrue(((Label) router.lookup("#requestDetail")).getText().contains("FF-000001"));
            assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("saved successfully"));
            button("backToRequests").fire();
        });
        drain();
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'REQUEST_CREATED'"));
        fx(() -> assertEquals(1, ((ListView<?>) router.lookup("#ownRequests")).getItems().size()));
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

    private void login(String username) throws Exception {
        fx(() -> {
            ((TextField) router.lookup("#username")).setText(username);
            ((PasswordField) router.lookup("#password")).setText("password");
            button("login").fire();
        });
        drain();
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
