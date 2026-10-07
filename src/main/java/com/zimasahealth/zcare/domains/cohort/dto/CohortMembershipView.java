package com.zimasahealth.zcare.domains.cohort.dto;

import java.time.LocalDate;

/** @param absorbed true when the member was already in the cohort and nothing new was written */
public record CohortMembershipView(Long id, Long cohortId, Long memberId, String inclusionMethod,
                                   String inclusionSourceDetail, String inclusionReason, LocalDate sourceDataDate,
                                   String status, boolean absorbed) {
}
