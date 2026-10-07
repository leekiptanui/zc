package com.zimasahealth.zcare.domains.enrolment.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code zc_consent_record}: ZCare-owned consent (ADR-002). At most one record per enrolment is in
 * force (captured, validated or scope-reduced; {@code uq_zc_consent_record_in_force}); a new
 * capture supersedes the old one, and history is kept.
 */
@Entity
@Table(name = "zc_consent_record")
@DynamicUpdate
public class ConsentRecord extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "wording_version", nullable = false, updatable = false)
    private String wordingVersion;

    @Column(name = "channel", nullable = false, updatable = false)
    private String channel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scope_content_classes", nullable = false)
    private String[] scopeContentClasses;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scope_sharing_purposes", nullable = false)
    private String[] scopeSharingPurposes = new String[0];

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(name = "captured_by", nullable = false, updatable = false)
    private String capturedBy;

    @Column(name = "evidence_ref", updatable = false)
    private String evidenceRef;

    @Column(name = "validated_by")
    private String validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_channel")
    private String revocationChannel;

    @Column(name = "revocation_verbatim")
    private String revocationVerbatim;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    protected ConsentRecord() {
    }

    public ConsentRecord(Long enrolmentId, Long memberId, String wordingVersion, String channel,
                         String[] scopeContentClasses, Instant capturedAt, String capturedBy, String evidenceRef) {
        this.enrolmentId = enrolmentId;
        this.memberId = memberId;
        this.status = "captured";
        this.wordingVersion = wordingVersion;
        this.channel = channel;
        this.scopeContentClasses = scopeContentClasses.clone();
        this.capturedAt = capturedAt;
        this.capturedBy = capturedBy;
        this.evidenceRef = evidenceRef;
    }

    public void validate(String by, Instant at) {
        this.status = "validated";
        this.validatedBy = by;
        this.validatedAt = at;
    }

    /** Withdrawal keeps the member's own words, not a paraphrase (UC-ZC-014 D1). */
    public void revoke(String channel, String verbatim, Instant at) {
        this.status = "revoked";
        this.revocationChannel = channel;
        this.revocationVerbatim = verbatim;
        this.revokedAt = at;
    }

    public void supersede(Instant at) {
        this.status = "superseded";
        this.supersededAt = at;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getStatus() {
        return status;
    }

    public String getWordingVersion() {
        return wordingVersion;
    }

    public String getChannel() {
        return channel;
    }

    public String[] getScopeContentClasses() {
        return scopeContentClasses.clone();
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public String getCapturedBy() {
        return capturedBy;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public String getValidatedBy() {
        return validatedBy;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationChannel() {
        return revocationChannel;
    }

    public String getRevocationVerbatim() {
        return revocationVerbatim;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }
}
