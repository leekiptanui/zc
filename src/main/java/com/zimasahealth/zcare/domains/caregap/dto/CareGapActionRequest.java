package com.zimasahealth.zcare.domains.caregap.dto;

import jakarta.validation.constraints.Size;

/**
 * @param reason   required; its absence is reported with its own code, not as a field error
 * @param evidence when closing, where the evidence of closure is kept
 */
public record CareGapActionRequest(@Size(max = 1000) String reason, @Size(max = 500) String evidence) {
}
