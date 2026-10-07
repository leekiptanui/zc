package com.zimasahealth.zcare.domains.referral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param outcome  {@code accepted}, {@code declined}, {@code in_progress}, {@code completed} or {@code cancelled}
 * @param reason   required to decline or cancel
 * @param evidence where the provider's or clinician's confirmation is kept
 */
public record RecordReferralOutcomeRequest(
        @NotBlank String outcome,
        @Size(max = 2000) String reason,
        @Size(max = 500) String evidence,
        Long receivingOrgId,
        @Size(max = 200) String receivingProviderRef) {
}
