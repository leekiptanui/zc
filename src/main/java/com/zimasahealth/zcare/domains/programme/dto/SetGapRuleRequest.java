package com.zimasahealth.zcare.domains.programme.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param periodStrategy how the period key of a gap is derived, for example {@code calendar_quarter}
 * @param definition     the rule itself, as the clinical package defines it
 */
public record SetGapRuleRequest(
        @NotBlank @Size(max = 64) String gapType,
        @NotBlank @Size(max = 64) String periodStrategy,
        @NotNull Map<String, Object> definition,
        @Size(max = 2000) String description) {
}
