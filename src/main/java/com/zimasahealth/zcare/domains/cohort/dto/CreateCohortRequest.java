package com.zimasahealth.zcare.domains.cohort.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param programmeVersionId must be a published version */
public record CreateCohortRequest(
        @NotNull Long programmeVersionId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description) {
}
