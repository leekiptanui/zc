package com.zimasahealth.zcare.domains.audit.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * {@code zc_domain_audit}, read side. Rows are written only by
 * {@link com.zimasahealth.zcare.common.audit.AuditWriter}; history cannot be edited or deleted
 * (trigger and privilege). The state snapshots are deliberately not mapped: queries return
 * metadata only.
 */
@Entity
@Table(name = "zc_domain_audit")
@Immutable
public class DomainAuditRecord extends AppendOnlyTenantEntity {

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "programme_version_id")
    private Long programmeVersionId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "operation", nullable = false)
    private String operation;

    @Column(name = "reason")
    private String reason;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "consent_wording_version")
    private String consentWordingVersion;

    @Column(name = "consent_channel")
    private String consentChannel;

    protected DomainAuditRecord() {
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActorId() {
        return actorId;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getOperation() {
        return operation;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getConsentWordingVersion() {
        return consentWordingVersion;
    }

    public String getConsentChannel() {
        return consentChannel;
    }
}
