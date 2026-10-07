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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

/**
 * Columns every mutable tenant table carries (docs/database/README.md). The database owns the
 * rest of the row's bookkeeping: {@code zc_touch_row()} bumps {@code row_version}, sets
 * {@code updated_at} and refuses any change to {@code id}, {@code tenant_id} or {@code created_*},
 * which is why those are mapped as not updatable.
 *
 * <p>Ids are never supplied: they are {@code GENERATED ALWAYS AS IDENTITY}. The tenant comes
 * from the request context, so a service cannot write a row for another tenant; row-level
 * security would refuse it anyway.
 */
@MappedSuperclass
public abstract class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    // Starts at 1 to satisfy ck_*_row_version; the touch trigger and Hibernate both add 1 per update.
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 1;

    @PrePersist
    void beforeInsert() {
        tenantId = TenantContext.require().id();
        createdAt = Times.now();
        createdBy = CurrentActor.idOrSystem();
        updatedBy = createdBy;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedBy = CurrentActor.idOrSystem();
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Integer getRowVersion() {
        return rowVersion;
    }
}
