package com.zimasahealth.zcare.domains.enrolment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A suspension always carries its reason (UC-ZC-010 E3). */
public record SuspendEnrolmentRequest(@NotBlank @Size(max = 1000) String reason) {
}
