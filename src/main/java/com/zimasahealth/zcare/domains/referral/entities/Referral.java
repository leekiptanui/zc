package com.zimasahealth.zcare.domains.referral.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_referral}: a coordination record, not a clinical order. It completes only on provider
 * or clinician confirmation ({@code ck_zc_referral_completion}); a member's own report is status,
 * never closure.
 */
@Entity
@Table(name = "zc_referral")
@DynamicUpdate
public class Referral extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "referral_type_id", nullable = false, updatable = false)
    private Long referralTypeId;

    @Column(name = "receiving_org_id")
    private Long receivingOrgId;

    @Column(name = "receiving_provider_ref")
    private String receivingProviderRef;

    @Column(name = "status", nullable = false)
    private String status = "created";

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "routed_at")
    private Instant routedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmation_source")
    private String confirmationSource;

    @Column(name = "evidence_ref")
    private String evidenceRef;

    @Column(name = "cancel_reason")
    private String cancelReason;

    protected Referral() {
    }

    public Referral(Long enrolmentId, Long memberId, Long referralTypeId, String reason) {
        this.enrolmentId = enrolmentId;
        this.memberId = memberId;
        this.referralTypeId = referralTypeId;
        this.reason = reason;
    }

    public boolean hasReceiver() {
        return receivingOrgId != null || (receivingProviderRef != null && !receivingProviderRef.isBlank());
    }

    public void route(Long organisationId, String providerRef, Instant at) {
        if (organisationId != null) {
            this.receivingOrgId = organisationId;
        }
        if (providerRef != null && !providerRef.isBlank()) {
            this.receivingProviderRef = providerRef;
        }
        if ("created".equals(status) && hasReceiver()) {
            this.status = "routed";
            this.routedAt = at;
        }
    }

    public void accept(Instant at) {
        this.status = "accepted";
        this.respondedAt = at;
    }

    public void decline(String reason, Instant at) {
        this.status = "declined";
        this.declineReason = reason;
        this.respondedAt = at;
    }

    public void start() {
        this.status = "in_progress";
    }

    public void complete(String confirmedBy, String source, String evidenceRef, Instant at) {
        this.status = "completed";
        this.confirmedBy = confirmedBy;
        this.confirmationSource = source;
        this.evidenceRef = evidenceRef;
        this.completedAt = at;
    }

    public void cancel(String reason) {
        this.status = "cancelled";
        this.cancelReason = reason;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getReferralTypeId() {
        return referralTypeId;
    }

    public Long getReceivingOrgId() {
        return receivingOrgId;
    }

    public String getReceivingProviderRef() {
        return receivingProviderRef;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRoutedAt() {
        return routedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public String getConfirmationSource() {
        return confirmationSource;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
