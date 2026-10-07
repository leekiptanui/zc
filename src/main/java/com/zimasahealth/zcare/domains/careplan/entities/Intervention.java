package com.zimasahealth.zcare.domains.careplan.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_intervention}: who does what for a plan; becomes a task when the plan activates. */
@Entity
@Table(name = "zc_intervention")
@DynamicUpdate
public class Intervention extends TenantEntity {

    @Column(name = "care_plan_id", nullable = false, updatable = false)
    private Long carePlanId;

    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "owner_role", nullable = false)
    private String ownerRole;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "frequency")
    private String frequency;

    @Column(name = "status", nullable = false)
    private String status = "planned";

    @Column(name = "status_reason")
    private String statusReason;

    protected Intervention() {
    }

    public Intervention(Long carePlanId, Long goalId, String ownerRole, String description, String frequency) {
        this.carePlanId = carePlanId;
        this.goalId = goalId;
        this.ownerRole = ownerRole;
        this.description = description;
        this.frequency = frequency;
    }

    public void activate() {
        this.status = "active";
    }

    public Long getCarePlanId() {
        return carePlanId;
    }

    public Long getGoalId() {
        return goalId;
    }

    public String getOwnerRole() {
        return ownerRole;
    }

    public String getDescription() {
        return description;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }
}
