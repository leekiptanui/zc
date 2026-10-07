package com.zimasahealth.zcare.domains.referral.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** {@code zc_provider_action}: append-only evidence of what a participating provider did. */
@Entity
@Table(name = "zc_provider_action")
@Immutable
public class ProviderAction extends AppendOnlyTenantEntity {

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "action_at", nullable = false)
    private Instant actionAt;

    @Column(name = "actor_ref")
    private String actorRef;

    @Column(name = "evidence_ref")
    private String evidenceRef;

    @Column(name = "notes")
    private String notes;

    protected ProviderAction() {
    }

    public ProviderAction(Long participationId, String subjectType, Long subjectId, String actionCode, Instant actionAt,
                          String actorRef, String evidenceRef, String notes) {
        this.participationId = participationId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.actionCode = actionCode;
        this.actionAt = actionAt;
        this.actorRef = actorRef;
        this.evidenceRef = evidenceRef;
        this.notes = notes;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public Instant getActionAt() {
        return actionAt;
    }

    public String getActorRef() {
        return actorRef;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public String getNotes() {
        return notes;
    }
}
