package com.zimasahealth.zcare.domains.referral.services;

/** Audit operation names of this domain; each equals the endpoint's {@code @AuditOperation}. */
public final class ReferralOperations {

    public static final String VIEW_REFERRALS = "VIEW_REFERRALS";
    public static final String CREATE_REFERRAL = "CREATE_REFERRAL";
    public static final String RECORD_REFERRAL_OUTCOME = "RECORD_REFERRAL_OUTCOME";
    public static final String REGISTER_PROVIDER_PARTICIPATION = "REGISTER_PROVIDER_PARTICIPATION";
    public static final String RECORD_PROVIDER_ACTION = "RECORD_PROVIDER_ACTION";
    public static final String CANCEL_REFERRAL = "CANCEL_REFERRAL";

    private ReferralOperations() {
    }
}
