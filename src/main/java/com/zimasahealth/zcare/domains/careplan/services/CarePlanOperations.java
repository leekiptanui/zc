package com.zimasahealth.zcare.domains.careplan.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class CarePlanOperations {

    public static final String VIEW_CARE_PLAN = "VIEW_CARE_PLAN";
    public static final String CREATE_CARE_PLAN = "CREATE_CARE_PLAN";
    public static final String SUBMIT_FOR_APPROVAL = "SUBMIT_FOR_APPROVAL";
    public static final String APPROVE_CARE_PLAN = "APPROVE_CARE_PLAN";
    public static final String REQUEST_PLAN_CHANGES = "REQUEST_PLAN_CHANGES";
    public static final String REJECT_CARE_PLAN = "REJECT_CARE_PLAN";
    public static final String REVISE_CARE_PLAN = "REVISE_CARE_PLAN";

    private CarePlanOperations() {
    }
}
