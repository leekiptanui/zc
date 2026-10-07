package com.zimasahealth.zcare.domains.programme.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/** A reading above {@code reviewAbove} or below {@code reviewBelow} raises a clinician review. */
public record SetThresholdRequest(
        @NotBlank String observationTypeCode,
        BigDecimal reviewAbove,
        BigDecimal reviewBelow) {
}
