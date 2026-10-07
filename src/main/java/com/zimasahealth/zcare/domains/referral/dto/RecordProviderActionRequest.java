package com.zimasahealth.zcare.domains.referral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param subjectType {@code referral}, {@code transition}, {@code care_plan}, {@code refill_request},
 *                    {@code task}, {@code enrolment} or {@code observation}
 */
public record RecordProviderActionRequest(
        @NotNull Long participationId,
        @NotBlank String subjectType,
        @NotNull Long subjectId,
        @NotBlank @Size(max = 64) String actionCode,
        @Size(max = 500) String evidenceRef,
        @Size(max = 2000) String notes) {
}
