package com.zimasahealth.zcare.domains.enrolment.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code zc_enrolment}: one member's accountable participation in one programme, the aggregate
 * root of participation (04A section 7.3). The programme version is pinned at invitation and
 * never changes ({@code trg_zc_enrolment_pins}).
 */
@Entity
@Table(name = "zc_enrolment")
@DynamicUpdate
public class Enrolment extends TenantEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "programme_id", nullable = false, updatable = false)
    private Long programmeId;

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "cohort_membership_id", updatable = false)
    private Long cohortMembershipId;

    @Column(name = "supersedes_enrolment_id", updatable = false)
    private Long supersedesEnrolmentId;

    @Column(name = "status", nullable = false)
    private String status = "invited";

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "responsible_cm")
    private String responsibleCm;

    @Column(name = "exited_at")
    private Instant exitedAt;

    @Column(name = "exit_reason")
    private String exitReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_snapshot")
    private Map<String, Object> eligibilitySnapshot;

    @Column(name = "eligibility_source_date")
    private LocalDate eligibilitySourceDate;

    protected Enrolment() {
    }

    public Enrolment(Long memberId, Long programmeId, Long programmeVersionId, Long cohortMembershipId,
                     Instant invitedAt) {
        this.memberId = memberId;
        this.programmeId = programmeId;
        this.programmeVersionId = programmeVersionId;
        this.cohortMembershipId = cohortMembershipId;
        this.invitedAt = invitedAt;
    }

    public void activate(String responsibleCm, Instant at) {
        this.status = "active";
        this.responsibleCm = responsibleCm;
        this.activatedAt = at;
    }

    public void exit(String status, String reason, Instant at) {
        this.status = status;
        this.exitReason = reason;
        this.exitedAt = at;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getProgrammeId() {
        return programmeId;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public Long getCohortMembershipId() {
        return cohortMembershipId;
    }

    public Long getSupersedesEnrolmentId() {
        return supersedesEnrolmentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public String getResponsibleCm() {
        return responsibleCm;
    }

    public Instant getExitedAt() {
        return exitedAt;
    }

    public String getExitReason() {
        return exitReason;
    }

    public Map<String, Object> getEligibilitySnapshot() {
        return eligibilitySnapshot;
    }

    public LocalDate getEligibilitySourceDate() {
        return eligibilitySourceDate;
    }
}
