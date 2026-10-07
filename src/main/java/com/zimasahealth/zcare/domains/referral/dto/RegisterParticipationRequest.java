package com.zimasahealth.zcare.domains.referral.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterParticipationRequest(
        @NotNull Long programmeVersionId,
        @NotNull Long organisationId,
        @Size(max = 200) String agreementRef) {
}
