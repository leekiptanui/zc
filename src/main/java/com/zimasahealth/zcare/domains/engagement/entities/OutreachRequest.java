package com.zimasahealth.zcare.domains.engagement.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_outreach_request}: ZCare owns the request; delivery belongs to the channel. Health
 * content always needs an enrolment ({@code ck_zc_outreach_request_health_content}).
 */
@Entity
@Table(name = "zc_outreach_request")
@DynamicUpdate
public class OutreachRequest extends TenantEntity {

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "enrolment_id", updatable = false)
    private Long enrolmentId;

    @Column(name = "content_class", nullable = false, updatable = false)
    private String contentClass;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "template_ref")
    private String templateRef;

    @Column(name = "status", nullable = false)
    private String status = "requested";

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "suppression_reason")
    private String suppressionReason;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "cancel_reason")
    private String cancelReason;

    protected OutreachRequest() {
    }

    public OutreachRequest(Long memberId, Long enrolmentId, String contentClass, String purpose, String templateRef,
                           Instant requestedAt, Instant scheduledFor) {
        this.memberId = memberId;
        this.enrolmentId = enrolmentId;
        this.contentClass = contentClass;
        this.purpose = purpose;
        this.templateRef = templateRef;
        this.requestedAt = requestedAt;
        this.scheduledFor = scheduledFor;
    }

    /** Held, not sent, until {@code scheduledFor}; it is never dropped. */
    public void hold(String reason, Instant until) {
        this.status = "held";
        this.holdReason = reason;
        this.scheduledFor = until;
    }

    public void cancel(String reason) {
        this.status = "cancelled";
        this.cancelReason = reason;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public String getContentClass() {
        return contentClass;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getTemplateRef() {
        return templateRef;
    }

    public String getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public String getSuppressionReason() {
        return suppressionReason;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
