package com.zimasahealth.zcare.domains.carework.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class TaskOperations {

    public static final String VIEW_WORK_QUEUE = "VIEW_WORK_QUEUE";
    public static final String ASSIGN_TASK = "ASSIGN_TASK";
    public static final String COMPLETE_TASK = "COMPLETE_TASK";
    /** Tasks raised by the service itself, not by an endpoint. */
    public static final String RAISE_TASK = "RAISE_TASK";
    public static final String CANCEL_TASK = "CANCEL_TASK";

    private TaskOperations() {
    }
}
