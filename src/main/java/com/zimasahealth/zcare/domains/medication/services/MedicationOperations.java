package com.zimasahealth.zcare.domains.medication.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class MedicationOperations {

    public static final String CREATE_REFILL_REQUEST = "CREATE_REFILL_REQUEST";
    public static final String RECORD_FULFILMENT = "RECORD_FULFILMENT";
    public static final String CANCEL_REFILL_REQUEST = "CANCEL_REFILL_REQUEST";

    private MedicationOperations() {
    }
}
