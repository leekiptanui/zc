package com.zimasahealth.zcare.domains.programme.entities;

import java.math.BigDecimal;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_observation_type}: a measurement a programme version requires, with its one unit. */
@Entity
@Table(name = "zc_observation_type")
@DynamicUpdate
public class ObservationType extends TenantEntity {

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "loinc_code")
    private String loincCode;

    @Column(name = "plausible_min")
    private BigDecimal plausibleMin;

    @Column(name = "plausible_max")
    private BigDecimal plausibleMax;

    protected ObservationType() {
    }

    public ObservationType(Long programmeVersionId, String code, String name, String unit, String loincCode,
                           BigDecimal plausibleMin, BigDecimal plausibleMax) {
        this.programmeVersionId = programmeVersionId;
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.loincCode = loincCode;
        this.plausibleMin = plausibleMin;
        this.plausibleMax = plausibleMax;
    }

    /** A copy for a new version of the programme. */
    public ObservationType copyTo(Long versionId) {
        return new ObservationType(versionId, code, name, unit, loincCode, plausibleMin, plausibleMax);
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

    public String getUnit() {
        return unit;
    }

    public String getLoincCode() {
        return loincCode;
    }

    public BigDecimal getPlausibleMin() {
        return plausibleMin;
    }

    public BigDecimal getPlausibleMax() {
        return plausibleMax;
    }
}
