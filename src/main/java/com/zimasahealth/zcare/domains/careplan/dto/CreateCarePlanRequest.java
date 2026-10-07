package com.zimasahealth.zcare.domains.careplan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A draft care plan for an active, consented enrolment (UC-ZC-002). */
public record CreateCarePlanRequest(
        @NotNull Long enrolmentId,
        @Size(max = 2000) String summary,
        @NotEmpty @Valid List<GoalInput> goals,
        @Valid List<InterventionInput> interventions) {

    /**
     * A goal is measurable: a target value with its unit, or target criteria (UC-ZC-002 E4).
     *
     * @param goalTypeCode a goal type of the enrolment's programme version
     */
    public record GoalInput(
            @NotBlank String goalTypeCode,
            @NotBlank @Size(max = 1000) String description,
            BigDecimal targetValue,
            @Size(max = 32) String targetUnit,
            @Size(max = 1000) String targetCriteria,
            LocalDate targetDate) {
    }

    /**
     * @param ownerRole the role that carries it out, such as {@code care_manager}
     * @param goalIndex index into {@code goals} of the goal it serves; omitted for plan-level work
     */
    public record InterventionInput(
            @NotBlank @Size(max = 1000) String description,
            @NotBlank String ownerRole,
            @Size(max = 200) String frequency,
            @PositiveOrZero Integer goalIndex) {
    }
}
