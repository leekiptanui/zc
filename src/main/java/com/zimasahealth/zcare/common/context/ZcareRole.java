package com.zimasahealth.zcare.common.context;

import java.util.Locale;
import java.util.Optional;

/**
 * The eight ZCare roles (04A section 18), in precedence order: when a caller holds several, the
 * first listed is their primary role.
 */
public enum ZcareRole {
    CLINICIAN("clinician"),
    CARE_MANAGER("care_manager"),
    PROGRAMME_ADMIN("programme_admin"),
    PROVIDER_COORDINATOR("provider_coordinator"),
    PAYER_MANAGER("payer_manager"),
    EMPLOYER_SPONSOR("employer_sponsor"),
    PLATFORM_ADMIN("platform_admin"),
    MEMBER("member");

    private final String code;

    ZcareRole(String code) {
        this.code = code;
    }

    /** The role as it appears in tokens, task assignments and the database. */
    public String code() {
        return code;
    }

    /** Spring Security authority, used by {@code hasRole(...)} expressions. */
    public String authority() {
        return "ROLE_" + name();
    }

    /** Accepts {@code care_manager}, {@code care-manager} and {@code CARE_MANAGER}. */
    public static Optional<ZcareRole> fromCode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ZcareRole role : values()) {
            if (role.code.equals(normalised)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
