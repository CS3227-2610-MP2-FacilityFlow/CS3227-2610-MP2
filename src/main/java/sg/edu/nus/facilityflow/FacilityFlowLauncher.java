package sg.edu.nus.facilityflow;

import javafx.application.Application;

/** Classpath launcher for the authenticated application. */
public final class FacilityFlowLauncher {
    private FacilityFlowLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(FacilityFlowApplication.class, args);
    }
}
