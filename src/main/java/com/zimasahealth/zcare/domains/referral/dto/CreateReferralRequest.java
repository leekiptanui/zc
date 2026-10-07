package com.zimasahealth.zcare.domains.referral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A referral; with a receiver named, it is routed at once. */
public record CreateReferralRequest(
        @NotNull Long enrolmentId,
        @NotBlank String referralTypeCode,
        @NotBlank @Size(max = 2000) String reason,
        Long receivingOrgId,
        @Size(max = 200) String receivingProviderRef) {
}
