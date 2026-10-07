package com.zimasahealth.zcare.domains.assessment.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * {@code zc_risk_classification}: append-only programme priority tier, never a diagnosis. An
 * override is a new row pointing back at the one it overrides.
 */
@Entity
@Table(name = "zc_risk_classification")
@Immutable
public class RiskClassification extends AppendOnlyTenantEntity {

    @Column(name = "enrolment_id", nullable = false)
    private Long enrolmentId;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "overrides_id")
    private Long overridesId;

    @Column(name = "priority_tier", nullable = false)
    private String priorityTier;

    @Column(name = "derived_from", nullable = false)
    private String derivedFrom;

    @Column(name = "is_override", nullable = false)
    private boolean override;

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "rationale")
    private String rationale;

    @Column(name = "classified_at", nullable = false)
    private Instant classifiedAt;

    protected RiskClassification() {
    }

    /** A tier derived from a completed assessment. */
    public static RiskClassification fromAssessment(Long enrolmentId, Long assessmentId, String tier, String rationale,
                                                    Instant at) {
        RiskClassification classification = new RiskClassification();
        classification.enrolmentId = enrolmentId;
        classification.assessmentId = assessmentId;
        classification.priorityTier = tier;
        classification.derivedFrom = "assessment";
        classification.rationale = rationale;
        classification.classifiedAt = at;
        return classification;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public Long getOverridesId() {
        return overridesId;
    }

    public String getPriorityTier() {
        return priorityTier;
    }

    public String getDerivedFrom() {
        return derivedFrom;
    }

    public boolean isOverride() {
        return override;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public String getRationale() {
        return rationale;
    }

    public Instant getClassifiedAt() {
        return classifiedAt;
    }
}
