package com.zimasahealth.zcare.domains.cohort.entities;

import java.time.Instant;
import java.time.LocalDate;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import com.zimasahealth.zcare.common.time.Times;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_cohort_run}: one identification attempt, kept whether it succeeded, failed or was held. */
@Entity
@Table(name = "zc_cohort_run")
@DynamicUpdate
public class CohortRun extends TenantEntity {

    @Column(name = "cohort_id", nullable = false, updatable = false)
    private Long cohortId;

    @Column(name = "status", nullable = false)
    private String status = "running";

    @Column(name = "rule_reference", nullable = false)
    private String ruleReference;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "source_data_date")
    private LocalDate sourceDataDate;

    @Column(name = "result_count")
    private Integer resultCount;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    protected CohortRun() {
    }

    public CohortRun(Long cohortId, String ruleReference) {
        this.cohortId = cohortId;
        this.ruleReference = ruleReference;
        this.startedAt = Times.now();
    }

    public void complete(int resultCount, LocalDate sourceDataDate) {
        this.status = "completed";
        this.resultCount = resultCount;
        this.sourceDataDate = sourceDataDate;
        this.finishedAt = Times.now();
    }

    public void fail(String reason) {
        this.status = "failed";
        this.failureReason = reason;
        this.finishedAt = Times.now();
    }

    public void hold(int resultCount, String reason) {
        this.status = "pending_review";
        this.resultCount = resultCount;
        this.holdReason = reason;
    }

    public Long getCohortId() {
        return cohortId;
    }

    public String getStatus() {
        return status;
    }

    public String getRuleReference() {
        return ruleReference;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public LocalDate getSourceDataDate() {
        return sourceDataDate;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
