package com.zimasahealth.zcare.domains.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param orgType free text until the approved list exists, for example {@code provider} or {@code employer} */
public record CreateOrganisationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 64) String orgType) {
}
