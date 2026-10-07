package com.zimasahealth.zcare.domains.medication.entities;

import java.time.LocalDate;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_medication_coordination}: a reference to a medication plan held in the provider's
 * system. ZCare never prescribes or dispenses; it coordinates.
 */
@Entity
@Table(name = "zc_medication_coordination")
@DynamicUpdate
public class MedicationCoordination extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "plan_ref", nullable = false)
    private String planRef;

    @Column(name = "plan_ref_system", nullable = false)
    private String planRefSystem;

    @Column(name = "medication_display")
    private String medicationDisplay;

    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "end_reason")
    private String endReason;

    protected MedicationCoordination() {
    }

    public MedicationCoordination(Long enrolmentId, String planRef, String planRefSystem, String medicationDisplay,
                                  LocalDate startedOn) {
        this.enrolmentId = enrolmentId;
        this.planRef = planRef;
        this.planRefSystem = planRefSystem;
        this.medicationDisplay = medicationDisplay;
        this.startedOn = startedOn;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public String getPlanRef() {
        return planRef;
    }

    public String getPlanRefSystem() {
        return planRefSystem;
    }

    public String getMedicationDisplay() {
        return medicationDisplay;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public String getEndReason() {
        return endReason;
    }
}
