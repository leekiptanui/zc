package com.zimasahealth.zcare.domains.medication.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A refill for an existing medication coordination, or for a provider plan named by
 * {@code planRef} and {@code planRefSystem}, whose coordination record is created.
 */
public record CreateRefillRequest(
        @NotNull Long enrolmentId,
        Long medicationCoordinationId,
        @Size(max = 200) String planRef,
        @Size(max = 64) String planRefSystem,
        @Size(max = 200) String medicationDisplay,
        LocalDate dueOn) {
}
