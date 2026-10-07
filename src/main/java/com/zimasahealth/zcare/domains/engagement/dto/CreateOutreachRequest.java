package com.zimasahealth.zcare.domains.engagement.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param enrolmentId  required for health content; omitted for a pre-enrolment invitation
 * @param contentClass {@code condition_neutral} or {@code health_content}
 * @param templateRef  the approved message template; wording awaits Privacy approval (OD-22)
 */
public record CreateOutreachRequest(
        @NotNull Long memberId,
        Long enrolmentId,
        @NotBlank String contentClass,
        @NotBlank @Size(max = 200) String purpose,
        @Size(max = 200) String templateRef,
        Instant scheduledFor) {
}
