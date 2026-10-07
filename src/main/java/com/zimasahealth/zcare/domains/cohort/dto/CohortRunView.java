package com.zimasahealth.zcare.domains.cohort.dto;

import java.time.Instant;
import java.time.LocalDate;

/** @param newMembers memberships this run added; zero unless it completed */
public record CohortRunView(Long id, Long cohortId, String status, String ruleReference, Instant startedAt,
                            Instant finishedAt, LocalDate sourceDataDate, Integer resultCount, String holdReason,
                            String failureReason, int newMembers) {
}
