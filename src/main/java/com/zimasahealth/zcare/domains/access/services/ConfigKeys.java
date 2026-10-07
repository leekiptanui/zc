package com.zimasahealth.zcare.domains.access.services;

/** Settings other domains read through {@link ConfigService}, as (scope, key) pairs. */
public final class ConfigKeys {

    /** "true" once Privacy has approved the assisted-consent script (UC-ZC-001 V2; OD-30). */
    public static final String PRIVACY = "privacy";
    public static final String ASSISTED_CONSENT_APPROVED = "assisted_consent_privacy_approved";

    /** Minimum hours between two health-content messages to one member (UC-ZC-006 2a). */
    public static final String OUTREACH = "outreach";
    public static final String MIN_GAP_HOURS = "min_gap_hours";

    /** Employer minimum population; may only raise the DEC-014 floor of 10 (ADR-009). */
    public static final String REPORTING = "reporting";
    public static final String MIN_POPULATION_THRESHOLD = "min_population_threshold";

    /** A payer-identified cohort larger than this is held for review (UC-ZC-011 E2). */
    public static final String COHORT = "cohort";
    public static final String MAX_PLAUSIBLE_VOLUME = "max_plausible_volume";

    private ConfigKeys() {
    }
}
