package com.zimasahealth.zcare.common.consent;

/** Consent state of an enrolment, from its consent in force or, failing that, its latest record. */
public enum ConsentState {
    /** No consent record exists. */
    NONE,
    CAPTURED,
    VALIDATED,
    SCOPE_REDUCED,
    REVOKED,
    SUPERSEDED;

    public String code() {
        return name().toLowerCase();
    }
}
