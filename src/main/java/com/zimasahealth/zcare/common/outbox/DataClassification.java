package com.zimasahealth.zcare.common.outbox;

/**
 * An event's data classification. Stored lowercase, as {@code ck_zc_integration_outbox_classification}
 * requires; 04B's upper-case {@code PHI} is an open casing question (OD-28).
 */
public enum DataClassification {
    PHI("phi"),
    NON_PHI("non_phi");

    private final String value;

    DataClassification(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
