package com.zimasahealth.zcare.domains.caregap.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** A gap with its explanation: rule version, reason and the data evaluated. */
public record CareGapView(Long id, Long enrolmentId, Long memberId, Long programmeVersionId, String gapType,
                          Integer ruleVersion, String periodKey, String status, String detectReason,
                          Map<String, Object> evaluatedData, Instant detectedAt, LocalDate dueOn,
                          String actionReason, String actionedBy, Instant actionedAt) {
}
