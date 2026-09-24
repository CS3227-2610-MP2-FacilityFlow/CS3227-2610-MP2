package sg.edu.nus.facilityflow.ui.requester;

import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.service.RequestValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** REQ-002/007/012 shared create/edit form with retained input on failure. */
public final class RequesterForm extends VBox {
    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final TextField location = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final ComboBox<ReportedUrgency> urgency = new ComboBox<>();
    private final Map<String, Label> fieldErrors = new LinkedHashMap<>();
    private final Label feedback = new Label();
    private final RequestValidator validator;
    private final Consumer<RequestDraft> submit;

    public RequesterForm(List<String> categories) {
        this(categories, null);
    }

    public RequesterForm(List<String> categories, Consumer<RequestDraft> submit) {
        this(categories, submit, null);
    }

    public RequesterForm(List<String> categories, Consumer<RequestDraft> submit, MaintenanceRequest request) {
        super(8);
        this.submit = submit;
        validator = new RequestValidator(Set.copyOf(categories));
        setPadding(new Insets(24));
        getStyleClass().add("card");
        setMaxWidth(800);
        var heading = new Label(request == null ? "New maintenance request" : "Edit " + request.displayId());
        heading.getStyleClass().add("section-title");
        heading.setWrapText(true);
        var notice = new Label(submit == null ? "Development preview — checks fields only. Nothing is saved."
                : "Describe the problem. Required fields are marked *.");
        notice.setWrapText(true);
        notice.getStyleClass().add("secondary-text");
        getChildren().addAll(heading, notice);
        description.setPrefRowCount(4);
        description.setWrapText(true);
        category.getItems().setAll(categories);
        category.setMaxWidth(Double.MAX_VALUE);
        category.setPromptText("Choose a category");
        urgency.setMaxWidth(Double.MAX_VALUE);
        urgency.setPromptText("Choose urgency");
        title.setPromptText("e.g. Leaking tap in the pantry");
        location.setPromptText("Building, floor and room");
        urgency.getItems().setAll(ReportedUrgency.values());
        urgency.setConverter(new StringConverter<ReportedUrgency>() {
            @Override
            public String toString(ReportedUrgency value) {
                return value == null ? "" : switch (value) {
                    case LOW -> "Low";
                    case NORMAL -> "Normal";
                    case HIGH -> "High";
                    case EMERGENCY -> "Emergency";
                };
            }

            @Override
            public ReportedUrgency fromString(String value) {
                throw new UnsupportedOperationException("Urgency must be selected from the list");
            }
        });
        addField("title", "Title * (5–100 characters)", title);
        addField("description", "Description * (10–2,000 characters)", description);
        addField("location", "Location * (2–120 characters)", location);
        addField("category", "Category *", category);
        addField("urgency", "Reported urgency *", urgency);
        if (request != null) {
            title.setText(request.title());
            description.setText(request.description());
            location.setText(request.location());
            category.setValue(request.category());
            urgency.setValue(request.reportedUrgency());
        }
        var validate = new Button(submit == null ? "Check details"
                : request == null ? "Submit request" : "Save changes");
        validate.setId("validate");
        validate.getStyleClass().add("primary-button");
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
        error.getStyleClass().add("feedback-error");
        error.managedProperty().bind(error.visibleProperty());
        fieldErrors.put(id, error);
        getChildren().addAll(label, control, error);
    }

    private void validateDetails() {
        var draft = new RequestDraft(title.getText(), description.getText(),
                location.getText(), category.getValue(), urgency.getValue());
        var errors = validator.validate(draft);
        fieldErrors.forEach((field, label) -> {
            label.setText(errors.containsKey(field) ? "Error: " + errors.get(field) : "");
            label.setVisible(errors.containsKey(field));
        });
        if (errors.isEmpty() && submit != null) {
            setSubmitting(true);
            submit.accept(draft);
            return;
        }
        feedback.setText(errors.isEmpty()
                ? "Details are valid. Nothing has been saved in this preview."
                : "Check the guidance beside each field. Your entries have been kept.");
    }

    public void setSubmitting(boolean busy) {
        setDisable(busy);
        feedback.setText(busy ? "Saving request…" : "");
    }

    public void showFailure(String message) {
        setSubmitting(false);
        feedback.setText(message);
    }
}
