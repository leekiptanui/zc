package com.zimasahealth.zcare.domains.carework.dto;

import java.time.Instant;

/**
 * A task another domain raises, such as a clinician review after a reading crosses its
 * threshold.
 *
 * @param originType a {@code ck_zc_task_origin_type} value naming what raised it
 */
public record SystemTask(long enrolmentId, long memberId, String taskType, String title, short priority,
                         String assignedToRole, Instant dueAt, String originType, Long originId) {
}
