package com.zimasahealth.zcare.domains.careplan.dto;

import java.util.List;

/**
 * In-process: a plan became active. Care work turns each intervention into a task in the same
 * transaction (ZCR-WRK-002).
 */
public record CarePlanActivatedEvent(long carePlanId, long enrolmentId, long memberId,
                                     List<ActivatedIntervention> interventions) {

    public record ActivatedIntervention(long interventionId, String ownerRole, String description) {
    }
}
