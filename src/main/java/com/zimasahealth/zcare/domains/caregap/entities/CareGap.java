package com.zimasahealth.zcare.domains.caregap.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code zc_care_gap}: explainable by construction. Every gap carries the rule version that
 * raised it, the reason and the data evaluated. One open gap per member, version, type and
 * period ({@code uq_zc_care_gap_dedup}). Suppression is not deletion: the gap stays reportable.
 */
@Entity
@Table(name = "zc_care_gap")
@DynamicUpdate
public class CareGap extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "gap_rule_id", nullable = false, updatable = false)
    private Long gapRuleId;

    @Column(name = "gap_type", nullable = false, updatable = false)
    private String gapType;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private Integer ruleVersion;

    @Column(name = "gap_key", nullable = false, updatable = false)
    private String gapKey;

    @Column(name = "period_key", nullable = false, updatable = false)
    private String periodKey;

    @Column(name = "status", nullable = false)
    private String status = "detected";

    @Column(name = "detect_reason", nullable = false, updatable = false)
    private String detectReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluated_data", nullable = false, updatable = false)
    private Map<String, Object> evaluatedData;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "assigned_task_id")
    private Long assignedTaskId;

    @Column(name = "action_reason")
    private String actionReason;

    @Column(name = "actioned_by")
    private String actionedBy;

    @Column(name = "actioned_at")
    private Instant actionedAt;

    @Column(name = "reopened_count", nullable = false)
    private Integer reopenedCount = 0;

    @Column(name = "last_reopened_at")
    private Instant lastReopenedAt;

    protected CareGap() {
    }

    public CareGap(Long enrolmentId, Long memberId, Long programmeVersionId, Long gapRuleId, String gapType,
                   Integer ruleVersion, String periodKey, String detectReason, Map<String, Object> evaluatedData,
                   Instant detectedAt, LocalDate dueOn) {
        this.enrolmentId = enrolmentId;
        this.memberId = memberId;
        this.programmeVersionId = programmeVersionId;
        this.gapRuleId = gapRuleId;
        this.gapType = gapType;
        this.ruleVersion = ruleVersion;
        this.periodKey = periodKey;
        this.gapKey = key(memberId, programmeVersionId, gapType, periodKey);
        this.detectReason = detectReason;
        this.evaluatedData = new LinkedHashMap<>(evaluatedData);
        this.detectedAt = detectedAt;
        this.dueOn = dueOn;
    }

    /** The 04A section 10.5 dedup key: member, programme version, gap type and period. */
    public static String key(Long memberId, Long programmeVersionId, String gapType, String periodKey) {
        return "member:" + memberId + "|version:" + programmeVersionId + "|type:" + gapType + "|period:" + periodKey;
    }

    public boolean isOpen() {
        return "detected".equals(status) || "assigned".equals(status);
    }

    /** {@code closed} or {@code suppressed}, with who, when and why. */
    public void action(String status, String reason, String by, Instant at) {
        this.status = status;
        this.actionReason = reason;
        this.actionedBy = by;
        this.actionedAt = at;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public Long getGapRuleId() {
        return gapRuleId;
    }

    public String getGapType() {
        return gapType;
    }

    public Integer getRuleVersion() {
        return ruleVersion;
    }

    public String getGapKey() {
        return gapKey;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public String getStatus() {
        return status;
    }

    public String getDetectReason() {
        return detectReason;
    }

    public Map<String, Object> getEvaluatedData() {
        return evaluatedData;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public Long getAssignedTaskId() {
        return assignedTaskId;
    }

    public String getActionReason() {
        return actionReason;
    }

    public String getActionedBy() {
        return actionedBy;
    }

    public Instant getActionedAt() {
        return actionedAt;
    }

    public Integer getReopenedCount() {
        return reopenedCount;
    }

    public Instant getLastReopenedAt() {
        return lastReopenedAt;
    }
}
