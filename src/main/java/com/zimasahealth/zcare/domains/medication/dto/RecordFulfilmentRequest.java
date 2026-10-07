package com.zimasahealth.zcare.domains.medication.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param fulfilmentState    {@code member_reported}, {@code confirmed} or {@code not_fulfilled}
 * @param confirmationSource optional; it is derived from the caller's role and, if given, must match
 */
public record RecordFulfilmentRequest(@NotBlank String fulfilmentState, String confirmationSource) {
}
