package com.zimasahealth.zcare.domains.referral.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_provider_participation}: an organisation registered to deliver a programme version. */
@Entity
@Table(name = "zc_provider_participation")
@DynamicUpdate
public class ProviderParticipation extends TenantEntity {

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private Long organisationId;

    @Column(name = "status", nullable = false)
    private String status = "invited";

    @Column(name = "agreement_ref")
    private String agreementRef;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "exited_at")
    private Instant exitedAt;

    @Column(name = "exit_reason")
    private String exitReason;

    protected ProviderParticipation() {
    }

    /** A participation registered by the programme administrator is active from the start. */
    public static ProviderParticipation active(Long programmeVersionId, Long organisationId, String agreementRef,
                                               Instant joinedAt) {
        ProviderParticipation participation = new ProviderParticipation();
        participation.programmeVersionId = programmeVersionId;
        participation.organisationId = organisationId;
        participation.agreementRef = agreementRef;
        participation.status = "active";
        participation.joinedAt = joinedAt;
        return participation;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public String getStatus() {
        return status;
    }

    public String getAgreementRef() {
        return agreementRef;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getExitedAt() {
        return exitedAt;
    }

    public String getExitReason() {
        return exitReason;
    }
}
