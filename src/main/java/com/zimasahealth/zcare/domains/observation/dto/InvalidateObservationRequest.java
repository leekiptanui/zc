package com.zimasahealth.zcare.domains.observation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvalidateObservationRequest(@NotBlank @Size(max = 1000) String reason) {
}
