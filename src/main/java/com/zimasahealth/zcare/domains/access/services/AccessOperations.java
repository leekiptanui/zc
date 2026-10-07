package com.zimasahealth.zcare.domains.access.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class AccessOperations {

    public static final String VIEW_ORGANISATIONS = "VIEW_ORGANISATIONS";
    public static final String CREATE_ORGANISATION = "CREATE_ORGANISATION";
    public static final String VIEW_CONFIG = "VIEW_CONFIG";
    public static final String SET_CONFIG = "SET_CONFIG";

    private AccessOperations() {
    }
}
