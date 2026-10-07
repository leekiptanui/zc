package com.zimasahealth.zcare.domains.assessment.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param questions the questions to administer, from the pinned template version
 * @param resumed   true when an open assessment of the same version already existed
 */
public record AssessmentView(Long id, Long enrolmentId, Long templateVersionId, String status, Instant assignedAt,
                             Instant dueAt, List<Object> questions, boolean resumed) {
}
