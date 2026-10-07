package com.zimasahealth.zcare.common.api;

/**
 * The envelope's {@code auditTrail}. {@code operation} equals the {@code zc_domain_audit.operation}
 * written for the same call (04B section 5).
 */
public record AuditContext(String operation, String userId, String correlationId) {
}
