package com.zimasahealth.zcare.domains.observation.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class ObservationOperations {

    public static final String CAPTURE_OBSERVATION = "CAPTURE_OBSERVATION";
    public static final String INVALIDATE_OBSERVATION = "INVALIDATE_OBSERVATION";
    public static final String VIEW_OBSERVATION_TREND = "VIEW_OBSERVATION_TREND";

    private ObservationOperations() {
    }
}
