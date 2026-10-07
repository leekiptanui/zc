package com.zimasahealth.zcare.common.audit;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.context.RequestCorrelation;
import com.zimasahealth.zcare.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the domain audit trail (04A INV-7; M02-07). Runs inside the caller's transaction, so an
 * audited change and its audit row commit or roll back together.
 *
 * <p>The table belongs to {@code domains.audit}; the writer lives here because every domain
 * needs it and infrastructure must not depend on a domain (PKG-04; OD-08). It uses plain JDBC
 * and maps no entity, so the audit domain remains the table's only mapper.
 */
@Component
public class AuditWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        String actor = CurrentActor.idOrSystem();
        jdbc.update("""
                INSERT INTO zc_domain_audit (tenant_id, actor_id, organisation_id, member_id,
                    programme_version_id, entity_type, entity_id, operation, previous_state, new_state,
                    reason, correlation_id, consent_wording_version, consent_channel, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """,
                TenantContext.require().id(), actor, entry.organisationId(), entry.memberId(),
                entry.programmeVersionId(), entry.entityType(), entry.entityId(), entry.operation(),
                toJson(entry.previousState()), toJson(entry.newState()), entry.reason(),
                RequestCorrelation.correlationId(), entry.consentWordingVersion(), entry.consentChannel(), actor);
    }

    private String toJson(Map<String, Object> state) {
        if (state == null) {
            return null;
        }
        try {
            return json.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit state is not serialisable", e);
        }
    }
}
