package com.meditrack.enums;

/** Category chosen when uploading a Medical Document (not a test report). */
public enum DocumentType {
    PREVIOUS_RECORD("Previous Medical Record"),
    LAB_REPORT("Lab Report"),
    XRAY_SCAN("X-Ray Scan"),
    PRESCRIPTION_DOC("Prescription"),
    DISCHARGE_SUMMARY("Discharge Summary"),
    INSURANCE("Insurance Document"),
    OTHER("Other");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    /** Human-readable name, used for download file names (e.g. "Lab Report"). */
    public String getLabel() {
        return label;
    }
}
