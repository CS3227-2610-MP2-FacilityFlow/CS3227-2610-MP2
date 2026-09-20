package facilityflow.model;

/** Requester-reported urgency, distinct from Manager priority (LIF-005). */
public enum ReportedUrgency {
    LOW("Low"), NORMAL("Normal"), HIGH("High"), EMERGENCY("Emergency");

    private final String label;

    ReportedUrgency(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
