package com.zimasahealth.zcare.common.outbox;

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
 * Writes domain events to the transactional outbox in the same transaction as the aggregate
 * change that produced them (04A section 15; 04D section 17; M02-10). Publishing them is the
 * outbox drain's job (M18).
 *
 * <p>The row carries the 04B section 12 envelope: its id is the event id, and tenant,
 * correlation, occurrence time, aggregate, payload and classification are columns.
 */
@Component
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emit(DomainEventType type, String aggregateType, long aggregateId, Map<String, Object> payload,
                     DataClassification classification) {
        jdbc.update("""
                INSERT INTO zc_integration_outbox (tenant_id, event_type, aggregate_type, aggregate_id,
                    correlation_id, payload, data_classification, created_by)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """,
                TenantContext.require().id(), type.name(), aggregateType, aggregateId,
                RequestCorrelation.correlationId(), toJson(payload), classification.value(),
                CurrentActor.idOrSystem());
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event payload is not serialisable", e);
        }
    }
}
