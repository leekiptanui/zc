package com.zimasahealth.zcare.domains.medication.entities;

import java.time.Instant;
import java.time.LocalDate;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_refill_request}: routing and fulfilment are tracked separately. "Confirmed" needs a
 * pharmacy or provider source, and the database enforces it too
 * ({@code ck_zc_refill_confirmed_requires_authorised_source}).
 */
@Entity
@Table(name = "zc_refill_request")
@DynamicUpdate
public class RefillRequest extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "medication_coordination_id", nullable = false, updatable = false)
    private Long medicationCoordinationId;

    @Column(name = "status", nullable = false)
    private String status = "requested";

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "is_overdue", nullable = false)
    private boolean overdue;

    @Column(name = "routed_to_ref")
    private String routedToRef;

    @Column(name = "fulfilment_state", nullable = false)
    private String fulfilmentState = "pending";

    @Column(name = "confirmation_source")
    private String confirmationSource;

    @Column(name = "fulfilment_recorded_at")
    private Instant fulfilmentRecordedAt;

    @Column(name = "fulfilment_recorded_by")
    private String fulfilmentRecordedBy;

    @Column(name = "concluded_at")
    private Instant concludedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    protected RefillRequest() {
    }

    public RefillRequest(Long enrolmentId, Long medicationCoordinationId, LocalDate dueOn) {
        this.enrolmentId = enrolmentId;
        this.medicationCoordinationId = medicationCoordinationId;
        this.dueOn = dueOn;
    }

    public boolean isOpen() {
        return "requested".equals(status) || "routed".equals(status) || "in_progress".equals(status);
    }

    public void recordFulfilment(String state, String source, String recordedBy, Instant at) {
        this.fulfilmentState = state;
        this.confirmationSource = source;
        this.fulfilmentRecordedBy = recordedBy;
        this.fulfilmentRecordedAt = at;
        this.status = "concluded";
        this.concludedAt = at;
    }

    public void cancel(String reason) {
        this.status = "cancelled";
        this.cancelReason = reason;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getMedicationCoordinationId() {
        return medicationCoordinationId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public String getRoutedToRef() {
        return routedToRef;
    }

    public String getFulfilmentState() {
        return fulfilmentState;
    }

    public String getConfirmationSource() {
        return confirmationSource;
    }

    public Instant getFulfilmentRecordedAt() {
        return fulfilmentRecordedAt;
    }

    public String getFulfilmentRecordedBy() {
        return fulfilmentRecordedBy;
    }

    public Instant getConcludedAt() {
        return concludedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
