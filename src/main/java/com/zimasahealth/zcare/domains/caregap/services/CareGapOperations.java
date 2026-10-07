package com.zimasahealth.zcare.domains.caregap.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class CareGapOperations {

    public static final String VIEW_CARE_GAPS = "VIEW_CARE_GAPS";
    public static final String DETECT_CARE_GAP = "DETECT_CARE_GAP";
    public static final String SUPPRESS_CARE_GAP = "SUPPRESS_CARE_GAP";
    public static final String CLOSE_CARE_GAP = "CLOSE_CARE_GAP";

    private CareGapOperations() {
    }
}
