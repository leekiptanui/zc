package com.zimasahealth.zcare.domains.observation.entities;

import java.math.BigDecimal;
import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_observation}: a programme-required measurement with its provenance. A wrong value is
 * invalidated, never edited ({@code trg_zc_observation_correction_only}).
 */
@Entity
@Table(name = "zc_observation")
@DynamicUpdate
public class Observation extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "observation_type_id", nullable = false, updatable = false)
    private Long observationTypeId;

    @Column(name = "value_numeric", nullable = false, updatable = false)
    private BigDecimal valueNumeric;

    @Column(name = "unit", nullable = false, updatable = false)
    private String unit;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "source", nullable = false, updatable = false)
    private String source;

    @Column(name = "source_ref", updatable = false)
    private String sourceRef;

    @Column(name = "loinc_code", updatable = false)
    private String loincCode;

    @Column(name = "is_invalidated", nullable = false)
    private boolean invalidated;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "invalidated_by")
    private String invalidatedBy;

    @Column(name = "invalidation_reason")
    private String invalidationReason;

    protected Observation() {
    }

    public Observation(Long enrolmentId, Long memberId, Long programmeVersionId, Long observationTypeId,
                       BigDecimal valueNumeric, String unit, Instant observedAt, String source, String sourceRef,
                       String loincCode) {
        this.enrolmentId = enrolmentId;
        this.memberId = memberId;
        this.programmeVersionId = programmeVersionId;
        this.observationTypeId = observationTypeId;
        this.valueNumeric = valueNumeric;
        this.unit = unit;
        this.observedAt = observedAt;
        this.source = source;
        this.sourceRef = sourceRef;
        this.loincCode = loincCode;
    }

    public void invalidate(String by, String reason, Instant at) {
        this.invalidated = true;
        this.invalidatedBy = by;
        this.invalidationReason = reason;
        this.invalidatedAt = at;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public Long getObservationTypeId() {
        return observationTypeId;
    }

    public BigDecimal getValueNumeric() {
        return valueNumeric;
    }

    public String getUnit() {
        return unit;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public String getSource() {
        return source;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getLoincCode() {
        return loincCode;
    }

    public boolean isInvalidated() {
        return invalidated;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public String getInvalidatedBy() {
        return invalidatedBy;
    }

    public String getInvalidationReason() {
        return invalidationReason;
    }
}
