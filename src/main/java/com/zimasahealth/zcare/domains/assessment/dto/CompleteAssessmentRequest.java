package com.zimasahealth.zcare.domains.assessment.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/** @param answers question code to answer; every required question must be answered */
public record CompleteAssessmentRequest(@NotNull Map<String, String> answers) {
}
