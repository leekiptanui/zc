package com.zimasahealth.zcare.common.api;

import com.fasterxml.jackson.annotation.JsonValue;

/** The envelope's {@code status} (ENG-STD-SB-001 section 2.1). */
public enum EnvelopeStatus {
    /** The operation did what was asked. */
    SUCCESS,
    /** Infrastructure failure: HTTP 500, never a business rule. */
    ERROR,
    /** A business rule spoke: HTTP 200 with one or more typed exceptions (04B section 6). */
    EXCEPTION,
    /** Accepted for asynchronous completion: HTTP 202. */
    PENDING;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
