package com.zimasahealth.zcare.domains.careplan.entities;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_goal}: a measurable goal of a plan ({@code ck_zc_goal_measurable}). */
@Entity
@Table(name = "zc_goal")
@DynamicUpdate
public class Goal extends TenantEntity {

    @Column(name = "care_plan_id", nullable = false, updatable = false)
    private Long carePlanId;

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "goal_type_id", nullable = false)
    private Long goalTypeId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "target_value")
    private BigDecimal targetValue;

    @Column(name = "target_unit")
    private String targetUnit;

    @Column(name = "target_criteria")
    private String targetCriteria;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "status_reason")
    private String statusReason;

    protected Goal() {
    }

    public Goal(Long carePlanId, Long programmeVersionId, Long goalTypeId, String description, BigDecimal targetValue,
                String targetUnit, String targetCriteria, LocalDate targetDate) {
        this.carePlanId = carePlanId;
        this.programmeVersionId = programmeVersionId;
        this.goalTypeId = goalTypeId;
        this.description = description;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
        this.targetCriteria = targetCriteria;
        this.targetDate = targetDate;
    }

    public Goal copyTo(Long planId) {
        return new Goal(planId, programmeVersionId, goalTypeId, description, targetValue, targetUnit, targetCriteria,
                targetDate);
    }

    public Long getCarePlanId() {
        return carePlanId;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public Long getGoalTypeId() {
        return goalTypeId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public String getTargetUnit() {
        return targetUnit;
    }

    public String getTargetCriteria() {
        return targetCriteria;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }
}
