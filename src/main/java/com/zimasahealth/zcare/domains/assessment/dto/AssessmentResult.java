package com.zimasahealth.zcare.domains.assessment.dto;

import java.math.BigDecimal;

/**
 * @param priorityTier programme priority, never a diagnosis (ZCR-ASM-004); null when the template
 *                     has no scoring rules
 */
public record AssessmentResult(Long assessmentId, Long enrolmentId, String status, BigDecimal score,
                               String priorityTier, Long riskClassificationId, String note) {
}
