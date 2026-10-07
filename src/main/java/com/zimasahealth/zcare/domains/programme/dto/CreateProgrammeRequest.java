package com.zimasahealth.zcare.domains.programme.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new programme and its draft version 1.
 *
 * @param careModel             free text until the approved list of care models exists (OD-15)
 * @param consentWordingVersion the consent wording members must accept before activation
 * @param content               the rest of the clinical package, held as given
 */
public record CreateProgrammeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 64) String careModel,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 32) String consentWordingVersion,
        Map<String, Object> content) {
}
