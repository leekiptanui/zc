package com.zimasahealth.zcare.domains.access.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new setting. It never edits history: it adds a row that takes effect at {@code effectiveFrom},
 * which defaults to now and may not lie in the past.
 */
public record SetConfigRequest(
        @NotBlank @Size(max = 64) String scope,
        @NotBlank @Size(max = 128) String key,
        @NotBlank @Size(max = 4000) String value,
        Instant effectiveFrom,
        @NotBlank @Size(max = 1000) String changeReason) {
}
