package com.zimasahealth.zcare.domains.enrolment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ending an enrolment. Exit and consent withdrawal are distinct acts (UC-ZC-014 section 2.2): a
 * member may leave yet keep their consent, so revoking it is an explicit choice.
 *
 * @param reason        {@code member_request}, {@code consent_declined}, {@code no_response},
 *                      {@code eligibility_lost}, {@code programme_completed}, {@code clinical_direction}
 *                      or {@code administrative}
 * @param revokeConsent also withdraw the consent in force
 * @param verbatim      the member's own words, required when consent is revoked
 */
public record WithdrawEnrolmentRequest(
        @NotBlank String reason,
        boolean revokeConsent,
        @Size(max = 2000) String verbatim) {
}
