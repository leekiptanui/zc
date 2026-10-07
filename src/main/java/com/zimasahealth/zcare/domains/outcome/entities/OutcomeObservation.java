package com.zimasahealth.zcare.domains.outcome.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_outcome_observation}: one value of a versioned outcome measure, for an enrolment or,
 * with no enrolment, for the programme as a whole. Incomplete data is marked, never filled in.
 */
@Entity
@Table(name = "zc_outcome_observation")
@DynamicUpdate
public class OutcomeObservation extends TenantEntity {

    @Column(name = "outcome_definition_id", nullable = false, updatable = false)
    private Long outcomeDefinitionId;

    @Column(name = "measure_version", nullable = false, updatable = false)
    private Integer measureVersion;

    @Column(name = "enrolment_id", updatable = false)
    private Long enrolmentId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "value_numeric")
    private BigDecimal valueNumeric;

    @Column(name = "numerator")
    private BigDecimal numerator;

    @Column(name = "denominator")
    private BigDecimal denominator;

    @Column(name = "is_incomplete", nullable = false)
    private boolean incomplete;

    @Column(name = "incomplete_reason")
    private String incompleteReason;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected OutcomeObservation() {
    }

    public OutcomeObservation(Long outcomeDefinitionId, Integer measureVersion, Long enrolmentId, LocalDate periodStart,
                              LocalDate periodEnd, BigDecimal valueNumeric, BigDecimal numerator,
                              BigDecimal denominator, String incompleteReason, Instant observedAt) {
        this.outcomeDefinitionId = outcomeDefinitionId;
        this.measureVersion = measureVersion;
        this.enrolmentId = enrolmentId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.valueNumeric = valueNumeric;
        this.numerator = numerator;
        this.denominator = denominator;
        this.incomplete = incompleteReason != null;
        this.incompleteReason = incompleteReason;
        this.observedAt = observedAt;
    }

    public Long getOutcomeDefinitionId() {
        return outcomeDefinitionId;
    }

    public Integer getMeasureVersion() {
        return measureVersion;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getValueNumeric() {
        return valueNumeric;
    }

    public BigDecimal getNumerator() {
        return numerator;
    }

    public BigDecimal getDenominator() {
        return denominator;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public String getIncompleteReason() {
        return incompleteReason;
    }

    public Instant getObservedAt() {
        return observedAt;
    }
}
