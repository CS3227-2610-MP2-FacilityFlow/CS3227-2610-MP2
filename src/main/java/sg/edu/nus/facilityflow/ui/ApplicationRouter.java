package sg.edu.nus.facilityflow.ui;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.AuthenticationService;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.service.ManagerRequestService;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.ui.manager.ManagerDashboardController;
import sg.edu.nus.facilityflow.ui.manager.ManagerDashboardView;
import sg.edu.nus.facilityflow.ui.requester.RequesterDashboardView;
import sg.edu.nus.facilityflow.ui.technician.TechnicianDashboardView;

/** AUT-014/015/032: identity comes only from the authentication service. */
public final class ApplicationRouter extends BorderPane {
    private final AuthenticationService auth;
    private final RequesterRequestService requester;
    private final ManagerRequestService manager;
    private final List<String> categories;
    private final UiTasks tasks;
    private AuthenticatedSession session;
    private int navigation;
    private RequesterDashboardView requesterView;
    private long requesterOwner;

    public ApplicationRouter(AuthenticationService auth, RequesterRequestService requester,
                             ManagerRequestService manager, List<String> categories, UiTasks tasks) {
        this.auth = auth;
        this.requester = requester;
        this.manager = manager;
        this.categories = categories;
        this.tasks = tasks;
        showLogin("");
    }

    private record LoginResult(AuthenticatedSession session, UserAccount account) {
    }

    private void showLogin(String message) {
        int screen = ++navigation;
        setTop(null);
        setBottom(null);
        var login = new LoginView((username, password) -> {
            tasks.run(() -> {
                var issued = auth.login(username, password);
                try {
                    return new LoginResult(issued, auth.currentUser(issued));
                } catch (RuntimeException error) {
                    auth.logout(issued);
                    throw error;
                }
            }, result -> {
                if (screen != navigation) {
                    auth.logout(result.session());
                    return;
                }
                session = result.session();
                route(result.account());
            }, error -> {
                if (screen == navigation && getCenter() instanceof LoginView view) {
                    view.showError(UiTasks.safeMessage(error));
                }
            });
        });
        setCenter(login);
        if (!message.isEmpty()) {
            login.showError(message);
        }
    }

    private void route(UserAccount account) {
        navigation++;
        setBottom(null);
        var logout = new Button("Log out");
        logout.setId("logout");
        logout.setOnAction(event -> {
            auth.logout(session);
            session = null;
            requesterView = null;
            showLogin("");
        });
        var refresh = new Button("Refresh account");
        refresh.setId("refreshAccount");
        refresh.setOnAction(event -> refreshIdentity());
        var password = new Button("Change password");
        password.setId("changePassword");
        password.setOnAction(event -> showPasswordChange());
        var header = new HBox(14, new Label(account.displayName() + " — " + account.role()),
                refresh, password, logout);
        header.setPadding(new Insets(16));
        header.disableProperty().bind(tasks.busy());
        setTop(header);
        if (account.id() != requesterOwner) {
            requesterView = null;
        }
        switch (account.role()) {
            case REQUESTER -> {
                if (requesterView == null) {
                    requesterOwner = account.id();
                    requesterView = new RequesterDashboardView(requester, session, categories, tasks, this::handleFailure);
                } else {
                    requesterView.resume(session);
                }
                setCenter(requesterView);
            }
            case FACILITIES_MANAGER -> setCenter(new ManagerDashboardView(
                    new ManagerDashboardController(manager, session), tasks, this::handleFailure).root());
            case TECHNICIAN -> setCenter(new TechnicianDashboardView());
        }
    }

    private void refreshIdentity() {
        var caller = session;
        int screen = navigation;
        tasks.run(() -> auth.currentUser(caller), account -> {
            if (screen == navigation) {
                route(account);
            }
        }, error -> {
            if (screen == navigation) {
                if (error instanceof AuthorizationException) {
                    auth.logout(caller);
                    session = null;
                    showLogin("Your session has ended. Sign in again to continue.");
                } else {
                    var notice = new Label(UiTasks.safeMessage(error));
                    notice.setWrapText(true);
                    setBottom(notice);
                }
            }
        });
    }

    private void handleFailure(Throwable error) {
        if (error instanceof AuthorizationException && session != null) {
            refreshIdentity();
        }
    }

    private void showPasswordChange() {
        var oldPassword = new PasswordField();
        oldPassword.setAccessibleText("Current password");
        var replacement = new PasswordField();
        replacement.setAccessibleText("New password (8–24 characters)");
        var save = new Button("Change password");
        var back = new Button("Back");
        back.setOnAction(event -> refreshIdentity());
        var feedback = new Label();
        feedback.setWrapText(true);
        var form = new VBox(10, new Label("Current password"), oldPassword,
                new Label("New password (8–24 characters)"), replacement, save, back, feedback);
        form.setPadding(new Insets(24));
        save.setOnAction(event -> {
            char[] current = oldPassword.getText().toCharArray();
            char[] next = replacement.getText().toCharArray();
            oldPassword.clear();
            replacement.clear();
            form.setDisable(true);
            var caller = session;
            tasks.run(() -> {
                auth.changePassword(caller, current, next);
                return true;
            }, ignored -> {
                form.setDisable(false);
                feedback.setText("Password changed. You remain signed in.");
            }, error -> {
                form.setDisable(false);
                feedback.setText(UiTasks.safeMessage(error));
                handleFailure(error);
            });
        });
        setCenter(form);
    }
}
