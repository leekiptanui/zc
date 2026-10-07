package com.zimasahealth.zcare.domains.reference.entities;

import java.time.LocalDate;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_member_reference}: the minimum ZCare keeps about a person, resolved to an individual
 * within a possibly household-level member number. A copy, never the member master:
 * {@code is_authoritative} is pinned false by {@code ck_zc_member_reference_non_auth}.
 */
@Entity
@Table(name = "zc_member_reference")
@DynamicUpdate
public class MemberReference extends TenantEntity {

    @Column(name = "source_system", nullable = false, updatable = false)
    private String sourceSystem;

    @Column(name = "source_member_number", nullable = false, updatable = false)
    private String sourceMemberNumber;

    @Column(name = "source_individual_ref", nullable = false, updatable = false)
    private String sourceIndividualRef;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "contact_msisdn")
    private String contactMsisdn;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;

    @Column(name = "is_authoritative", nullable = false, updatable = false)
    private boolean authoritative = false;

    protected MemberReference() {
    }

    public MemberReference(String sourceSystem, String sourceMemberNumber, String sourceIndividualRef,
                           String displayName, String contactMsisdn, LocalDate sourceDate) {
        this.sourceSystem = sourceSystem;
        this.sourceMemberNumber = sourceMemberNumber;
        this.sourceIndividualRef = sourceIndividualRef;
        this.displayName = displayName;
        this.contactMsisdn = contactMsisdn;
        this.sourceDate = sourceDate;
    }

    /** Takes newer copied details; an older source never overwrites a newer one. */
    public void refresh(String displayName, String contactMsisdn, LocalDate sourceDate) {
        if (sourceDate.isBefore(this.sourceDate)) {
            return;
        }
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (contactMsisdn != null) {
            this.contactMsisdn = contactMsisdn;
        }
        this.sourceDate = sourceDate;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceMemberNumber() {
        return sourceMemberNumber;
    }

    public String getSourceIndividualRef() {
        return sourceIndividualRef;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getContactMsisdn() {
        return contactMsisdn;
    }

    public LocalDate getSourceDate() {
        return sourceDate;
    }

    public boolean isAuthoritative() {
        return authoritative;
    }
}
