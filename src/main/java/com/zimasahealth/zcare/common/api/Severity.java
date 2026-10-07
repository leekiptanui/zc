package com.zimasahealth.zcare.common.api;

/** Severity of a business exception, which routes what the caller may do next (ENG-STD-SB-001 section 2.4). */
public enum Severity {
    /** Informational: the request was absorbed; proceed. */
    INFO,
    /** Correctable by the caller: retry or proceed as {@code nextActions} says. */
    WARNING,
    /** Needs a person named in {@code escalateTo}. */
    ESCALATE,
    /** Pipeline termination: {@code nextActions} is empty. */
    HARD_STOP
}
