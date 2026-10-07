package com.zimasahealth.zcare.domains.audit.dto;

import java.time.Instant;

/** Filters of the audit query; null means "any". {@code to} is exclusive. */
public record AuditQuery(Long memberId, String actorId, String correlationId, String operation, String entityType,
                         Instant from, Instant to) {
}
