package com.zimasahealth.zcare.domains.programme.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class ProgrammeOperations {

    public static final String VIEW_PROGRAMMES = "VIEW_PROGRAMMES";
    public static final String VIEW_PROGRAMME = "VIEW_PROGRAMME";
    public static final String CREATE_PROGRAMME = "CREATE_PROGRAMME";
    public static final String CREATE_PROGRAMME_VERSION = "CREATE_PROGRAMME_VERSION";
    public static final String ADD_OBSERVATION_TYPE = "ADD_OBSERVATION_TYPE";
    public static final String SET_OBSERVATION_THRESHOLD = "SET_OBSERVATION_THRESHOLD";
    public static final String ADD_GOAL_TYPE = "ADD_GOAL_TYPE";
    public static final String SET_GAP_RULE = "SET_GAP_RULE";
    public static final String SET_TASK_TEMPLATE = "SET_TASK_TEMPLATE";
    public static final String DEFINE_OUTCOME_MEASURE = "DEFINE_OUTCOME_MEASURE";
    public static final String RECORD_CLINICAL_APPROVAL = "RECORD_CLINICAL_APPROVAL";
    public static final String PUBLISH_PROGRAMME = "PUBLISH_PROGRAMME";
    public static final String RETIRE_PROGRAMME = "RETIRE_PROGRAMME";
    public static final String VIEW_REFERRAL_TYPES = "VIEW_REFERRAL_TYPES";
    public static final String CREATE_REFERRAL_TYPE = "CREATE_REFERRAL_TYPE";
    public static final String VIEW_ASSESSMENT_TEMPLATES = "VIEW_ASSESSMENT_TEMPLATES";
    public static final String CREATE_ASSESSMENT_TEMPLATE = "CREATE_ASSESSMENT_TEMPLATE";
    public static final String PUBLISH_ASSESSMENT_TEMPLATE = "PUBLISH_ASSESSMENT_TEMPLATE";

    private ProgrammeOperations() {
    }
}
