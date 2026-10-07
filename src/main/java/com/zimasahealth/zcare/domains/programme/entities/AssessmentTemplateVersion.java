package com.zimasahealth.zcare.domains.programme.entities;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code zc_assessment_template_version}: a questionnaire and its deterministic scoring rules.
 * Published versions are immutable ({@code trg_zc_assessment_template_version_immutable}).
 */
@Entity
@Table(name = "zc_assessment_template_version")
@DynamicUpdate
public class AssessmentTemplateVersion extends TenantEntity {

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private Integer versionNumber;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questionnaire", nullable = false)
    private Map<String, Object> questionnaire;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scoring_rules")
    private Map<String, Object> scoringRules;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected AssessmentTemplateVersion() {
    }

    public AssessmentTemplateVersion(Long templateId, Integer versionNumber, Map<String, Object> questionnaire,
                                     Map<String, Object> scoringRules) {
        this.templateId = templateId;
        this.versionNumber = versionNumber;
        this.questionnaire = new LinkedHashMap<>(questionnaire);
        this.scoringRules = scoringRules == null ? null : new LinkedHashMap<>(scoringRules);
    }

    public Long getTemplateId() {
        return templateId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getStatus() {
        return status;
    }

    public void publish(Instant at) {
        this.status = "published";
        this.publishedAt = at;
    }

    public void retire(Instant at) {
        this.status = "retired";
        this.retiredAt = at;
    }

    public Map<String, Object> getQuestionnaire() {
        return questionnaire;
    }

    public Map<String, Object> getScoringRules() {
        return scoringRules;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }
}
