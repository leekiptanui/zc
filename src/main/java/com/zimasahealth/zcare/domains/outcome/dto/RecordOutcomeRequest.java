package com.zimasahealth.zcare.domains.outcome.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A value of a defined outcome measure: {@code value}, or {@code numerator} with
 * {@code denominator}; or, when the data is missing, {@code incompleteReason}.
 *
 * @param enrolmentId set for a member-level outcome; omitted for an aggregate one
 */
public record RecordOutcomeRequest(
        @NotNull Long programmeVersionId,
        @NotBlank String measureCode,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        BigDecimal value,
        BigDecimal numerator,
        @Positive BigDecimal denominator,
        Long enrolmentId,
        @Size(max = 1000) String incompleteReason) {
}
