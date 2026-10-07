package com.zimasahealth.zcare.domains.carework.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_task_dependency}: a task cannot complete while a prerequisite is open
 * ({@code trg_zc_task_dependency_gate}). Removal is soft and carries its reason.
 */
@Entity
@Table(name = "zc_task_dependency")
@DynamicUpdate
public class TaskDependency extends TenantEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;

    @Column(name = "depends_on_task_id", nullable = false, updatable = false)
    private Long dependsOnTaskId;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private String removedBy;

    @Column(name = "removal_reason")
    private String removalReason;

    protected TaskDependency() {
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public String getRemovedBy() {
        return removedBy;
    }

    public String getRemovalReason() {
        return removalReason;
    }
}
