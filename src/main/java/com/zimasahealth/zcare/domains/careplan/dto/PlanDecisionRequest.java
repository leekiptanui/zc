package com.zimasahealth.zcare.domains.careplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A clinician's decision to return or reject a plan always carries its reasons. */
public record PlanDecisionRequest(@NotBlank @Size(max = 4000) String notes) {
}
