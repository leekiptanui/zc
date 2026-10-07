package com.zimasahealth.zcare.domains.assessment.entities;

import java.math.BigDecimal;
import java.time.Instant;
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
 * {@code zc_assessment}: a questionnaire assigned to an enrolment. The template version is pinned
 * at assignment ({@code trg_zc_assessment_pins}), so answers always score against the version
 * that was live when it was assigned.
 */
@Entity
@Table(name = "zc_assessment")
@DynamicUpdate
public class Assessment extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "template_version_id", nullable = false, updatable = false)
    private Long templateVersionId;

    @Column(name = "status", nullable = false)
    private String status = "assigned";

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "score")
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_detail")
    private Map<String, Object> scoreDetail;

    @Column(name = "cancel_reason")
    private String cancelReason;

    protected Assessment() {
    }

    public Assessment(Long enrolmentId, Long templateVersionId, Instant assignedAt, Instant dueAt) {
        this.enrolmentId = enrolmentId;
        this.templateVersionId = templateVersionId;
        this.assignedAt = assignedAt;
        this.dueAt = dueAt;
    }

    public void complete(BigDecimal score, Map<String, Object> scoreDetail, Instant at) {
        this.status = "completed";
        this.score = score;
        this.scoreDetail = scoreDetail == null ? null : new LinkedHashMap<>(scoreDetail);
        this.completedAt = at;
    }

    public boolean isOpen() {
        return "assigned".equals(status) || "in_progress".equals(status);
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Map<String, Object> getScoreDetail() {
        return scoreDetail;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
