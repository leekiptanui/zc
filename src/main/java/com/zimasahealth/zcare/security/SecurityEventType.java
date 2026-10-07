package com.zimasahealth.zcare.security;

/** {@code zc_security_event.event_type} values ({@code ck_zc_security_event_type}). */
public enum SecurityEventType {
    AUTHORISATION_DENIED,
    TENANT_VIOLATION_ATTEMPT,
    INVALID_WEBHOOK_SIGNATURE,
    BREAK_GLASS_USE,
    AUDIT_EXPORT,
    AUTHENTICATION_FAILED,
    AUTHENTICATION_SUCCEEDED,
    ACCOUNT_LOCKED;

    public String value() {
        return name().toLowerCase();
    }
}
