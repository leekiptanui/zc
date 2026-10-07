package com.zimasahealth.zcare.domains.careplan.dto;

/**
 * In-process: a plan now waits for a clinician. Care work raises the clinician's approval task;
 * the plan holds indefinitely, with no timeout, until a clinician decides (UC-ZC-002 E6).
 */
public record CarePlanSubmittedEvent(long carePlanId, long enrolmentId, long memberId, int versionNumber) {
}
