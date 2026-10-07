package com.zimasahealth.zcare.security;

import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.context.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes {@code zc_security_event} (04D section 11; M02-08): denials, tenant violations, audit
 * exports. Each event commits in its own transaction, so it survives the rollback of the request
 * that caused it. Writing is best effort: a failure is logged and never turns a 403 into a 500.
 */
@Component
public class SecurityEventWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventWriter.class);
    private static final int MAX_DETAIL = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public SecurityEventWriter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param tenantId the tenant concerned, or null when none is resolved yet
     * @param actorId  the caller, or null when unauthenticated
     * @param detail   metadata only: never health data (PRD section 9.3)
     */
    public void record(SecurityEventType type, Long tenantId, String actorId, HttpServletRequest request,
                       String detail) {
        String route = request != null ? request.getMethod() + " " + request.getRequestURI() : null;
        String address = request != null ? request.getRemoteAddr() : null;
        String trimmed = detail != null && detail.length() > MAX_DETAIL ? detail.substring(0, MAX_DETAIL) : detail;
        try {
            transactions.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO zc_security_event (tenant_id, event_type, actor_id, source_address, route,
                        correlation_id, detail, created_by)
                    VALUES (?, ?, ?, ?::inet, ?, ?, ?, ?)
                    """,
                    tenantId, type.value(), actorId, address, route, RequestCorrelation.correlationId(), trimmed,
                    actorId != null ? actorId : CurrentActor.SYSTEM));
        } catch (RuntimeException e) {
            log.error("Security event could not be written: type={}", type.value(), e);
        }
    }
}
