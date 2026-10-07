package com.zimasahealth.zcare.domains.careplan.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_care_plan}: a care-coordination artefact, not a clinical order. One version per row;
 * a revision is a new row that supersedes the active one once approved. Approval, once recorded,
 * cannot be rewritten ({@code trg_zc_care_plan_approval_immutable}); absence of approval is
 * never approval.
 */
@Entity
@Table(name = "zc_care_plan")
@DynamicUpdate
public class CarePlan extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "supersedes_plan_id", updatable = false)
    private Long supersedesPlanId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private Integer versionNumber;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    @Column(name = "summary")
    private String summary;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected CarePlan() {
    }

    public CarePlan(Long enrolmentId, Long programmeVersionId, Integer versionNumber, Long supersedesPlanId,
                    String summary) {
        this.enrolmentId = enrolmentId;
        this.programmeVersionId = programmeVersionId;
        this.versionNumber = versionNumber;
        this.supersedesPlanId = supersedesPlanId;
        this.summary = summary;
    }

    public void submit(Instant at) {
        this.status = "pending_approval";
        this.submittedAt = at;
    }

    public void approveAndActivate(String clinician, Instant at) {
        this.status = "active";
        this.approvedBy = clinician;
        this.approvedAt = at;
        this.activatedAt = at;
    }

    public void returnToDraft() {
        this.status = "draft";
    }

    public void reject(String reason) {
        this.status = "rejected";
        this.rejectionReason = reason;
    }

    /** Superseded by an approved revision; the approved version itself is preserved unchanged. */
    public void markRevised() {
        this.status = "revised";
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public Long getSupersedesPlanId() {
        return supersedesPlanId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
