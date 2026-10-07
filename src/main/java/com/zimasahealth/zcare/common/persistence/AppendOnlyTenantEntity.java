package com.zimasahealth.zcare.common.persistence;

import java.time.Instant;

import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

/**
 * Columns of an append-only tenant table: no {@code updated_*}, no {@code row_version}. Subclasses
 * are also {@code @Immutable}; a change is a new row, and the table's trigger and privileges
 * refuse UPDATE and DELETE anyway (04D section 11).
 */
@MappedSuperclass
public abstract class AppendOnlyTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @PrePersist
    void beforeInsert() {
        tenantId = TenantContext.require().id();
        createdAt = Times.now();
        createdBy = CurrentActor.idOrSystem();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
