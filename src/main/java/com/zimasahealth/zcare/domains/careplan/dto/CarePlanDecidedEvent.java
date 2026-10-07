package com.zimasahealth.zcare.domains.careplan.dto;

/**
 * In-process: a clinician decided on a pending plan, closing its approval task.
 *
 * @param outcome {@code approved}, {@code changes_requested} or {@code rejected}
 */
public record CarePlanDecidedEvent(long carePlanId, String outcome) {
}
