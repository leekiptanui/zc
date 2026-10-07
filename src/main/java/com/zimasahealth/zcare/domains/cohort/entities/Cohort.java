package com.zimasahealth.zcare.domains.cohort.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_cohort}: the population of one published programme version. A cohort starts as a
 * draft; releasing it (draft to active) is the human review gate, and nobody is invited from an
 * unreleased cohort (UC-ZC-011).
 */
@Entity
@Table(name = "zc_cohort")
@DynamicUpdate
public class Cohort extends TenantEntity {

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    @Column(name = "description")
    private String description;

    protected Cohort() {
    }

    public Cohort(Long programmeVersionId, String code, String name, String description) {
        this.programmeVersionId = programmeVersionId;
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }
}
