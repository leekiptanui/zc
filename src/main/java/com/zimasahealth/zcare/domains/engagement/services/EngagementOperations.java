package com.zimasahealth.zcare.domains.engagement.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class EngagementOperations {

    public static final String REQUEST_OUTREACH = "REQUEST_OUTREACH";
    public static final String CANCEL_OUTREACH = "CANCEL_OUTREACH";

    private EngagementOperations() {
    }
}
