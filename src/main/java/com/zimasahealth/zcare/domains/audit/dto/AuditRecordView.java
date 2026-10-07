package com.zimasahealth.zcare.domains.audit.dto;

import java.time.Instant;

/** One audit row, metadata only: never the clinical content of the change (UC-ZC-010 section 3.9-6). */
public record AuditRecordView(Long id, Instant occurredAt, String actorId, String operation, String entityType,
                              Long entityId, Long memberId, Long programmeVersionId, Long organisationId,
                              String correlationId, String reason, String consentWordingVersion,
                              String consentChannel) {
}
