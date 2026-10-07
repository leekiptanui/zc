package com.zimasahealth.zcare.domains.cohort.entities;

import java.time.Instant;
import java.time.LocalDate;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_cohort_membership}: eligibility to be invited. Membership, enrolment and consent are
 * three distinct gates. Every inclusion records how and why it happened.
 */
@Entity
@Table(name = "zc_cohort_membership")
@DynamicUpdate
public class CohortMembership extends TenantEntity {

    @Column(name = "cohort_id", nullable = false, updatable = false)
    private Long cohortId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "cohort_run_id", updatable = false)
    private Long cohortRunId;

    @Column(name = "inclusion_method", nullable = false, updatable = false)
    private String inclusionMethod;

    @Column(name = "inclusion_source_detail", updatable = false)
    private String inclusionSourceDetail;

    @Column(name = "inclusion_reason", nullable = false, updatable = false)
    private String inclusionReason;

    @Column(name = "source_data_date", nullable = false, updatable = false)
    private LocalDate sourceDataDate;

    @Column(name = "status", nullable = false)
    private String status = "included";

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private String removedBy;

    @Column(name = "removal_reason")
    private String removalReason;

    protected CohortMembership() {
    }

    public CohortMembership(Long cohortId, Long memberId, Long cohortRunId, String inclusionMethod,
                            String inclusionSourceDetail, String inclusionReason, LocalDate sourceDataDate) {
        this.cohortId = cohortId;
        this.memberId = memberId;
        this.cohortRunId = cohortRunId;
        this.inclusionMethod = inclusionMethod;
        this.inclusionSourceDetail = inclusionSourceDetail;
        this.inclusionReason = inclusionReason;
        this.sourceDataDate = sourceDataDate;
    }

    public Long getCohortId() {
        return cohortId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getCohortRunId() {
        return cohortRunId;
    }

    public String getInclusionMethod() {
        return inclusionMethod;
    }

    public String getInclusionSourceDetail() {
        return inclusionSourceDetail;
    }

    public String getInclusionReason() {
        return inclusionReason;
    }

    public LocalDate getSourceDataDate() {
        return sourceDataDate;
    }

    public String getStatus() {
        return status;
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
