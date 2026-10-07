package com.zimasahealth.zcare.common.consent;

import java.util.Optional;

/** What a consent covers and what an outreach carries ({@code ck_zc_consent_record_scope}). */
public enum ContentClass {
    /** Contact that reveals no condition, such as an invitation. */
    CONDITION_NEUTRAL("condition_neutral"),
    /** Anything that processes or reveals the member's health context. */
    HEALTH_CONTENT("health_content");

    private final String code;

    ContentClass(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<ContentClass> fromCode(String code) {
        for (ContentClass value : values()) {
            if (value.code.equals(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
