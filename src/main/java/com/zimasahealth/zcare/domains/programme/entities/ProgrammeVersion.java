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
 * {@code zc_programme_version}: draft, then published once a clinician has approved it, then
 * retired. A published version is immutable except for retirement
 * ({@code trg_zc_programme_version_immutable}); a change is a new version.
 *
 * <p>{@code content} holds the clinical package, which is still an external dependency. ZCare
 * reads two keys from it: {@code consentWordingVersion} and {@code observationThresholds}.
 */
@Entity
@Table(name = "zc_programme_version")
@DynamicUpdate
public class ProgrammeVersion extends TenantEntity {

    @Column(name = "programme_id", nullable = false, updatable = false)
    private Long programmeId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private Integer versionNumber;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content")
    private Map<String, Object> content;

    @Column(name = "clinical_approved_by")
    private String clinicalApprovedBy;

    @Column(name = "clinical_approved_at")
    private Instant clinicalApprovedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected ProgrammeVersion() {
    }

    public ProgrammeVersion(Long programmeId, Integer versionNumber, Map<String, Object> content) {
        this.programmeId = programmeId;
        this.versionNumber = versionNumber;
        this.content = content == null ? new LinkedHashMap<>() : new LinkedHashMap<>(content);
    }

    public Long getProgrammeId() {
        return programmeId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getContent() {
        return content == null ? Map.of() : content;
    }

    /** Replaces the content map, so the change is detected and written. */
    public void setContent(Map<String, Object> content) {
        this.content = new LinkedHashMap<>(content);
    }

    public String getClinicalApprovedBy() {
        return clinicalApprovedBy;
    }

    public Instant getClinicalApprovedAt() {
        return clinicalApprovedAt;
    }

    public void recordClinicalApproval(String clinician, Instant at) {
        this.clinicalApprovedBy = clinician;
        this.clinicalApprovedAt = at;
    }

    /** A clinical change to a draft voids its approval: a clinician must approve again. */
    public void clearClinicalApproval() {
        this.clinicalApprovedBy = null;
        this.clinicalApprovedAt = null;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(Instant retiredAt) {
        this.retiredAt = retiredAt;
    }
}
