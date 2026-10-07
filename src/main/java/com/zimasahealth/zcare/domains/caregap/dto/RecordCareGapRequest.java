package com.zimasahealth.zcare.domains.caregap.dto;

import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A detected gap, raised under the programme version's current rule for its type.
 *
 * @param periodKey     the period the gap belongs to, for example {@code 2026-Q4}
 * @param evaluatedData the data the rule evaluated: the gap's explanation
 */
public record RecordCareGapRequest(
        @NotNull Long enrolmentId,
        @NotBlank @Size(max = 64) String gapType,
        @NotBlank @Size(max = 32) String periodKey,
        @NotBlank @Size(max = 1000) String detectReason,
        @NotNull Map<String, Object> evaluatedData,
        LocalDate dueOn) {
}
