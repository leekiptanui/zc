package com.zimasahealth.zcare.domains.programme.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_programme}: draft, active, suspended or retired. Content lives in its versions. */
@Entity
@Table(name = "zc_programme")
@DynamicUpdate
public class Programme extends TenantEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "care_model", nullable = false)
    private String careModel;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    @Column(name = "description")
    private String description;

    protected Programme() {
    }

    public Programme(String code, String name, String careModel, String description) {
        this.code = code;
        this.name = name;
        this.careModel = careModel;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCareModel() {
        return careModel;
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
