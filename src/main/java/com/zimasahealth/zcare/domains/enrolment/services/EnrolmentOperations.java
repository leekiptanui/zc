package com.zimasahealth.zcare.domains.enrolment.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class EnrolmentOperations {

    public static final String VIEW_ENROLMENTS = "VIEW_ENROLMENTS";
    public static final String VIEW_ENROLMENT = "VIEW_ENROLMENT";
    public static final String INVITE_MEMBER = "INVITE_MEMBER";
    public static final String CAPTURE_CONSENT = "CAPTURE_CONSENT";
    public static final String ACTIVATE_ENROLMENT = "ACTIVATE_ENROLMENT";
    public static final String SUSPEND_ENROLMENT = "SUSPEND_ENROLMENT";
    public static final String RESUME_ENROLMENT = "RESUME_ENROLMENT";
    public static final String WITHDRAW_ENROLMENT = "WITHDRAW_ENROLMENT";
    public static final String VIEW_MEMBER_CONTEXT = "VIEW_MEMBER_CONTEXT";

    private EnrolmentOperations() {
    }
}
