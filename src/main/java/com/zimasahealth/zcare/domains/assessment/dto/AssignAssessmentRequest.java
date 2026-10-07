package com.zimasahealth.zcare.domains.assessment.dto;

import java.time.Instant;

import jakarta.validation.constraints.Size;

/** @param templateCode the questionnaire to assign; omitted, the most recently published one */
public record AssignAssessmentRequest(@Size(max = 64) String templateCode, Instant dueAt) {
}
