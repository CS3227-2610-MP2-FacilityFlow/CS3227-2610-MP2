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


    @Test
    @DisplayName("REQ-007/011/012 UIX-009 edit prefills details, retains failure input and disables pending actions")
    void editsRequestWithRollbackRetry() throws Exception {
        createOwnRequest();
        fx(() -> {
            button("editRequest").fire();
            router.applyCss();
            router.layout();
            assertEquals("Leaking pipe", ((TextField) router.lookup("#title")).getText());
            assertEquals("Water leaks under the sink.", ((TextArea) router.lookup("#description")).getText());
            assertEquals("Room 12", ((TextField) router.lookup("#location")).getText());
            assertEquals("Plumbing", ((ComboBox<?>) router.lookup("#category")).getValue());
            assertNotNull(((ComboBox<?>) router.lookup("#urgency")).getValue());
            ((TextField) router.lookup("#title")).setText("  Updated leaking pipe  ");
        });
        fixture.execute("CREATE TRIGGER reject_edit BEFORE INSERT ON audit_events BEGIN SELECT RAISE(ABORT, 'private error'); END");
        fx(() -> {
            button("validate").fire();
            button("validate").fire();
            assertEquals(1, work.size());
            assertTrue(button("validate").isDisabled());
            assertTrue(button("logout").isDisabled());
        });
        drain();
        fx(() -> {
            assertEquals("  Updated leaking pipe  ", ((TextField) router.lookup("#title")).getText());
            assertTrue(((Label) router.lookup("#feedback")).getText().contains("input has been kept"));
            assertFalse(button("validate").isDisabled());
        });
        fixture.execute("DROP TRIGGER reject_edit");
        fx(() -> button("validate").fire());
        drain();
        fx(() -> assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("saved successfully")));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests WHERE title = 'Updated leaking pipe'"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'REQUEST_EDITED'"));
    }

    @Test
    @DisplayName("REQ-A11 UIX-011/022 cancellation dialog defaults to keep, decline retains reason and writes nothing")
    void declinesCancellationSafely() throws Exception {
        createOwnRequest();
        fx(() -> {
            button("cancelRequest").fire();
            router.applyCss();
            router.layout();
            ((TextArea) router.lookup("#cancelReason")).setText("No longer needed");
        });
        answerCancellation(false);
        fx(() -> {
            assertEquals("No longer needed", ((TextArea) router.lookup("#cancelReason")).getText());
            assertTrue(work.isEmpty());
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests WHERE status = 'OPEN'"));
        assertEquals(0, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'REQUEST_CANCELLED'"));
    }

    @Test
    @DisplayName("REQ-008/011/012 UIX-009 cancellation failure retains reason; retry shows persisted terminal detail")
    void retriesCancellationAfterStorageFailure() throws Exception {
        createOwnRequest();
        fx(() -> {
            button("cancelRequest").fire();
            router.applyCss();
            router.layout();
            ((TextArea) router.lookup("#cancelReason")).setText("  No longer needed  ");
        });
        fixture.execute("CREATE TRIGGER reject_cancel BEFORE INSERT ON audit_events BEGIN SELECT RAISE(ABORT, 'private error'); END");
        answerCancellation(true);
        fx(() -> {
            assertTrue(button("confirmCancellation").isDisabled());
            assertTrue(button("keepRequest").isDisabled());
            button("confirmCancellation").fire();
            assertEquals(1, work.size());
        });
        drain();
        fx(() -> {
            assertEquals("  No longer needed  ", ((TextArea) router.lookup("#cancelReason")).getText());
            assertTrue(((Label) router.lookup("#cancelFeedback")).getText().contains("input has been kept"));
            assertFalse(button("confirmCancellation").isDisabled());
        });
        fixture.execute("DROP TRIGGER reject_cancel");
        answerCancellation(true);
        drain();
        fx(() -> {
            assertTrue(((Label) router.lookup("#requestDetail")).getText().contains("Status: CANCELLED"));
            assertEquals("Cancellation reason: No longer needed", ((Label) router.lookup("#cancellationReason")).getText());
            assertTrue(((Label) router.lookup("#requesterFeedback")).getText().contains("cancelled successfully"));
            assertNull(router.lookup("#editRequest"));
            assertNull(router.lookup("#cancelRequest"));
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'REQUEST_CANCELLED'"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("REQ-A10 REQ-012 stale edit/cancel retains input after Manager assignment")
    void retainsStaleMutationInput(boolean cancel) throws Exception {
        createOwnRequest();
        fx(() -> {
            button(cancel ? "cancelRequest" : "editRequest").fire();
            router.applyCss();
            router.layout();
            if (cancel) {
                ((TextArea) router.lookup("#cancelReason")).setText("No longer needed");
            } else {
                ((TextField) router.lookup("#title")).setText("Unsaved changed title");
            }
        });
        var manager = fixture.auth.login("manager", "password".toCharArray());
        fixture.manager.assignOpenRequest(manager, 1, 4, sg.edu.nus.facilityflow.model.ManagerPriority.HIGH);
        if (cancel) {
            answerCancellation(true);
        } else {
            fx(() -> button("validate").fire());
        }
        drain();
        fx(() -> {
            String message = ((Label) router.lookup(cancel ? "#cancelFeedback" : "#feedback")).getText();
            assertTrue(message.contains("OPEN"));
            assertEquals(cancel ? "No longer needed" : "Unsaved changed title", cancel
                    ? ((TextArea) router.lookup("#cancelReason")).getText()
                    : ((TextField) router.lookup("#title")).getText());
        });
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM maintenance_requests WHERE status = 'ASSIGNED'"));
    }

    private void createOwnRequest() throws Exception {
        login("owner");
        fillForm();
        fx(() -> button("validate").fire());
        drain();
    }

    private void answerCancellation(boolean confirm) throws Exception {
        var answered = new java.util.concurrent.CompletableFuture<Void>();
        fx(() -> {
            Platform.runLater(() -> {
                try {
                    var dialog = javafx.stage.Window.getWindows().stream()
                            .map(window -> window.getScene().lookup("#cancelConfirmation"))
                            .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
                    var pane = (javafx.scene.control.DialogPane) dialog;
                    var keep = (Button) pane.lookupButton(javafx.scene.control.ButtonType.CANCEL);
                    var cancel = (Button) pane.lookupButton(javafx.scene.control.ButtonType.OK);
                    assertTrue(keep.isDefaultButton());
                    assertFalse(cancel.isDefaultButton());
                    assertTrue(pane.getContentText().contains("FF-000001"));
                    assertTrue(pane.getContentText().contains("CANCELLED"));
                    (confirm ? cancel : keep).fire();
                    answered.complete(null);
                } catch (Throwable error) {
                    answered.completeExceptionally(error);
                    // Close any modal dialog even if an assertion failed.
                    javafx.stage.Window.getWindows().stream().filter(window -> window != stage)
                            .toList().forEach(javafx.stage.Window::hide);
                }
            });
            button("confirmCancellation").fire();
        });
        answered.get(5, java.util.concurrent.TimeUnit.SECONDS);
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
