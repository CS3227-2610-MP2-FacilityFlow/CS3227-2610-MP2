package facilityflow.ui.requester;

import facilityflow.model.ReportedUrgency;
import facilityflow.model.RequestDraft;
import facilityflow.service.RequestValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** REQ-002/012 form foundation; validation only, with no submission operation. */
public final class RequesterForm extends VBox {
    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final TextField location = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final ComboBox<ReportedUrgency> urgency = new ComboBox<>();
    private final Map<String, Label> fieldErrors = new LinkedHashMap<>();
    private final Label feedback = new Label();
    private final RequestValidator validator;

    public RequesterForm(List<String> categories) {
        super(8);
        validator = new RequestValidator(Set.copyOf(categories));
        setPadding(new Insets(24));
        var heading = new Label("New maintenance request");
        heading.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        var notice = new Label("Development preview — checks fields only. Nothing is saved.");
        notice.setWrapText(true);
        getChildren().addAll(heading, notice);
        description.setPrefRowCount(4);
        description.setWrapText(true);
        category.getItems().setAll(categories);
        urgency.getItems().setAll(ReportedUrgency.values());
        addField("title", "Title * (5–100 characters)", title);
        addField("description", "Description * (10–2,000 characters)", description);
        addField("location", "Location * (2–120 characters)", location);
        addField("category", "Category *", category);
        addField("urgency", "Reported urgency *", urgency);
        var validate = new Button("Check details");
        validate.setId("validate");
        validate.setOnAction(event -> validateDetails());
        feedback.setId("feedback");
        feedback.setWrapText(true);
        getChildren().addAll(validate, feedback);
    }

    private void addField(String id, String text, Control control) {
        control.setId(id);
        control.setAccessibleText(text);
        var label = new Label(text);
        label.setLabelFor(control);
        var error = new Label();
        error.setId(id + "Error");
        error.setWrapText(true);
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());
        fieldErrors.put(id, error);
        getChildren().addAll(label, control, error);
    }

    private void validateDetails() {
        var draft = new RequestDraft(title.getText(), description.getText(),
                location.getText(), category.getValue(), urgency.getValue());
        var errors = validator.validate(draft);
        fieldErrors.forEach((field, label) -> {
            label.setText(errors.getOrDefault(field, ""));
            label.setVisible(errors.containsKey(field));
        });
        feedback.setText(errors.isEmpty()
                ? "Details are valid. Nothing has been saved in this preview."
                : "Check the guidance beside each field. Your entries have been kept.");
    }
}
