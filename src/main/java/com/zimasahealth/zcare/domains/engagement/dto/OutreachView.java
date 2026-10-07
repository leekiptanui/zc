package com.zimasahealth.zcare.domains.engagement.dto;

import java.time.Instant;

/** @param scheduledFor for a held request, the next permissible send time */
public record OutreachView(Long id, Long memberId, Long enrolmentId, String contentClass, String purpose,
                           String templateRef, String status, Instant requestedAt, Instant scheduledFor,
                           String holdReason, String cancelReason) {
}
