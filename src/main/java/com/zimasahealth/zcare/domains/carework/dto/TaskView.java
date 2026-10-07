package com.zimasahealth.zcare.domains.carework.dto;

import java.time.Instant;

/** A thin task projection; {@code overdue} is computed against now. */
public record TaskView(Long id, Long enrolmentId, Long memberId, String taskType, String title, Short priority,
                       String status, String assignedToActor, String assignedToRole, Instant dueAt, String slaState,
                       boolean overdue, String originType, Long originId, Instant completedAt) {
}
