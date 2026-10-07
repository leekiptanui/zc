package com.zimasahealth.zcare.domains.access.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_organisation}: a payer, provider, employer or other body inside one tenant. */
@Entity
@Table(name = "zc_organisation")
@DynamicUpdate
public class Organisation extends TenantEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "org_type", nullable = false)
    private String orgType;

    @Column(name = "status", nullable = false)
    private String status = "active";

    protected Organisation() {
    }

    public Organisation(String code, String name, String orgType) {
        this.code = code;
        this.name = name;
        this.orgType = orgType;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getOrgType() {
        return orgType;
    }

    public String getStatus() {
        return status;
    }
}
