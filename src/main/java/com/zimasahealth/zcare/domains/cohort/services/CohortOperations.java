package com.zimasahealth.zcare.domains.cohort.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class CohortOperations {

    public static final String VIEW_COHORTS = "VIEW_COHORTS";
    public static final String VIEW_COHORT_MEMBERS = "VIEW_COHORT_MEMBERS";
    public static final String CREATE_COHORT = "CREATE_COHORT";
    public static final String ADD_COHORT_MEMBER = "ADD_COHORT_MEMBER";
    public static final String IDENTIFY_COHORT = "IDENTIFY_COHORT";
    public static final String RELEASE_COHORT = "RELEASE_COHORT";

    private CohortOperations() {
    }
}
