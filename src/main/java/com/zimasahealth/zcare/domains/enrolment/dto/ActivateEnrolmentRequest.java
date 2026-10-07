package com.zimasahealth.zcare.domains.enrolment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Activation names the care manager responsible for the member (04B section 20). */
public record ActivateEnrolmentRequest(@NotNull @Valid Responsibility responsibility) {

    public record Responsibility(@NotBlank @Size(max = 128) String careManagerId) {
    }
}
