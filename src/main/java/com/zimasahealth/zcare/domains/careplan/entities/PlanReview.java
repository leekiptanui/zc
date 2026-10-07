package com.zimasahealth.zcare.domains.careplan.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** {@code zc_plan_review}: append-only; every approval round and review is kept. */
@Entity
@Table(name = "zc_plan_review")
@Immutable
public class PlanReview extends AppendOnlyTenantEntity {

    @Column(name = "care_plan_id", nullable = false)
    private Long carePlanId;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "reviewed_by", nullable = false)
    private String reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "notes")
    private String notes;

    protected PlanReview() {
    }

    public PlanReview(Long carePlanId, String outcome, String reviewedBy, Instant reviewedAt, String notes) {
        this.carePlanId = carePlanId;
        this.outcome = outcome;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.notes = notes;
    }

    public Long getCarePlanId() {
        return carePlanId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getNotes() {
        return notes;
    }
}
