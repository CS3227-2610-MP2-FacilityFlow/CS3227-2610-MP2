package sg.edu.nus.facilityflow.ui;

import java.util.function.BiConsumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class LoginView extends VBox {
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Button login = new Button("Sign in");
    private final Label feedback = new Label();

    public LoginView(BiConsumer<String, char[]> submit) {
        super(12);
        setId("loginView");
        setPadding(new Insets(32));
        setAlignment(Pos.CENTER);
        getStyleClass().add("login-page");
        username.setId("username");
        username.setAccessibleText("Username");
        password.setId("password");
        password.setAccessibleText("Password");
        var usernameLabel = new Label("Username");
        usernameLabel.setLabelFor(username);
        var passwordLabel = new Label("Password");
        passwordLabel.setLabelFor(password);
        login.setId("login");
        login.setDefaultButton(true);
        feedback.setId("loginFeedback");
        feedback.setWrapText(true);
        login.setOnAction(event -> {
            char[] entered = password.getText().toCharArray();
            password.clear();
            setBusy(true);
            submit.accept(username.getText(), entered);
        });
        var brand = new Label("FacilityFlow");
        brand.getStyleClass().add("product-name");
        var heading = new Label("Welcome back");
        heading.getStyleClass().add("section-title");
        var help = new Label("Sign in to your maintenance workspace.");
        help.getStyleClass().add("secondary-text");
        help.setWrapText(true);
        username.setPromptText("Enter your username");
        password.setPromptText("Enter your password");
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);
        feedback.getStyleClass().add("feedback-label");
        var card = new VBox(12, brand, heading, help, usernameLabel, username,
                passwordLabel, password, login, feedback);
        card.getStyleClass().add("card");
        card.setMaxWidth(440);
        getChildren().add(card);
    }

    public void setBusy(boolean busy) {
        username.setDisable(busy);
        password.setDisable(busy);
        login.setDisable(busy);
        feedback.setText(busy ? "Signing in…" : "");
    }

    public void showError(String message) {
        setBusy(false);
        feedback.setText(message);
        password.requestFocus();
    }
}
