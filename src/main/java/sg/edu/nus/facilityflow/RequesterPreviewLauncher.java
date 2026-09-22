package sg.edu.nus.facilityflow;

import sg.edu.nus.facilityflow.ui.requester.RequesterPreview;
import javafx.application.Application;

/** Development entry point; this is not an authenticated application session. */
public final class RequesterPreviewLauncher {
    private RequesterPreviewLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(RequesterPreview.class, args);
    }
}
