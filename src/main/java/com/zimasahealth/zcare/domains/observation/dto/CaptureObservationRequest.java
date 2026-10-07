package com.zimasahealth.zcare.domains.observation.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param unit   must be the observation type's configured unit
 * @param source {@code member_reported}, {@code care_team_recorded}, {@code provider}, {@code laboratory}
 *               or {@code device}; a provider user records as {@code provider} only
 */
public record CaptureObservationRequest(
        @NotNull Long enrolmentId,
        @NotBlank String observationTypeCode,
        @NotNull BigDecimal value,
        @NotBlank String unit,
        @NotNull Instant observedAt,
        @NotBlank String source,
        @Size(max = 200) String sourceRef) {
}
