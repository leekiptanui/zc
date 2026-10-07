package com.zimasahealth.zcare.domains.programme.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param unit UCUM unit; {@code 1} for unitless. Every reading of this type must use it. */
public record AddObservationTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 32) String unit,
        @Size(max = 32) String loincCode,
        BigDecimal plausibleMin,
        BigDecimal plausibleMax) {
}
