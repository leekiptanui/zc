package com.zimasahealth.zcare.domains.programme.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_referral_type}: tenant-scoped, usable by any programme. */
@Entity
@Table(name = "zc_referral_type")
@DynamicUpdate
public class ReferralType extends TenantEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_platform_defined", nullable = false)
    private boolean platformDefined;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ReferralType() {
    }

    public ReferralType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isPlatformDefined() {
        return platformDefined;
    }

    public boolean isActive() {
        return active;
    }
}
