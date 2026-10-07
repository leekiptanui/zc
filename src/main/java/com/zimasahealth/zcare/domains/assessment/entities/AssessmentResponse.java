package com.zimasahealth.zcare.domains.assessment.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** {@code zc_assessment_response}: append-only; a changed answer is a new row, the latest wins. */
@Entity
@Table(name = "zc_assessment_response")
@Immutable
public class AssessmentResponse extends AppendOnlyTenantEntity {

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "question_code", nullable = false)
    private String questionCode;

    @Column(name = "answer_value")
    private String answerValue;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    protected AssessmentResponse() {
    }

    public AssessmentResponse(Long assessmentId, String questionCode, String answerValue, Instant answeredAt) {
        this.assessmentId = assessmentId;
        this.questionCode = questionCode;
        this.answerValue = answerValue;
        this.answeredAt = answeredAt;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public String getQuestionCode() {
        return questionCode;
    }

    public String getAnswerValue() {
        return answerValue;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
