package com.zimasahealth.zcare.common.error;

import java.util.List;

import com.zimasahealth.zcare.common.api.Severity;

/**
 * The {@code ZCareExceptionCodes} registry (04B section 6). A versioned contract: a code is never
 * renamed or reused, only added.
 *
 * <p>The first 21 entries are the 04B founding registry. The entries after them are not yet in
 * 04B and must be added there before the milestone that uses them closes (OD-17).
 */
public enum ZCareExceptionCode {

    // ---- 04B founding registry ----
    ZCARE_CONSENT_REQUIRED(Severity.ESCALATE, "CARE_MANAGER", "request_consent"),
    ZCARE_CONSENT_REVOKED(Severity.HARD_STOP, null),
    ZCARE_ENROLMENT_DUPLICATE(Severity.WARNING, null, "proceed"),
    ZCARE_GAP_DUPLICATE(Severity.INFO, null, "proceed"),
    ZCARE_GAP_SUPPRESS_REASON_REQUIRED(Severity.WARNING, null, "retry"),
    ZCARE_PROGRAMME_VERSION_IMMUTABLE(Severity.HARD_STOP, null),
    ZCARE_EXTERNAL_DEPENDENCY_UNAVAILABLE(Severity.WARNING, null, "retry"),
    ZCARE_PLAN_VERSION_IMMUTABLE(Severity.HARD_STOP, null),
    ZCARE_CLINICAL_APPROVAL_REQUIRED(Severity.HARD_STOP, null),
    ZCARE_CONFIG_HISTORY_IMMUTABLE(Severity.HARD_STOP, null),
    ZCARE_TASK_RESTRICTED(Severity.ESCALATE, "ROLE_OWNER", "escalate"),
    ZCARE_GAP_ACTION_RESTRICTED(Severity.ESCALATE, "ROLE_OWNER", "escalate"),
    ZCARE_COHORT_REVIEW_REQUIRED(Severity.ESCALATE, "PROGRAMME_ADMIN", "review"),
    ZCARE_FULFILMENT_SOURCE_REQUIRED(Severity.WARNING, null, "retry"),
    ZCARE_REFILL_DUPLICATE(Severity.INFO, null, "proceed"),
    ZCARE_OUTREACH_FREQUENCY_EXCEEDED(Severity.WARNING, null, "proceed"),
    ZCARE_AGGREGATE_THRESHOLD_NOT_MET(Severity.WARNING, null, "proceed"),
    ZCARE_THRESHOLD_RULES_MISSING(Severity.WARNING, null, "escalate"),
    VALIDATION_FIELD_INVALID(Severity.WARNING, null, "retry"),
    VALIDATION_FIELD_REQUIRED(Severity.WARNING, null, "retry"),
    IDEMPOTENCY_KEY_CONFLICT(Severity.HARD_STOP, null),

    // ---- Pending addition to 04B (OD-17) ----
    /** A reading beyond its configured threshold raised a clinician review (ADD-001 Part B). */
    ZCARE_THRESHOLD_REVIEW_REQUIRED(Severity.ESCALATE, "CLINICIAN", "escalate"),
    /** Another request changed the record first (optimistic lock on {@code row_version}). */
    ZCARE_CONCURRENT_MODIFICATION(Severity.WARNING, null, "retry"),
    /** Infrastructure failure; travels only on {@code status: error} envelopes with HTTP 500. */
    ZCARE_INTERNAL_ERROR(Severity.HARD_STOP, null);

    private final Severity severity;
    private final String escalateTo;
    private final List<String> nextActions;

    ZCareExceptionCode(Severity severity, String escalateTo, String... nextActions) {
        this.severity = severity;
        this.escalateTo = escalateTo;
        this.nextActions = List.of(nextActions);
    }

    public String code() {
        return name();
    }

    public Severity severity() {
        return severity;
    }

    public String escalateTo() {
        return escalateTo;
    }

    /** The default allow-list for this code; empty for every HARD_STOP. */
    public List<String> nextActions() {
        return nextActions;
    }
}
