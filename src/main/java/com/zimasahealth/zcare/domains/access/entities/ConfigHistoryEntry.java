package com.zimasahealth.zcare.domains.access.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * {@code zc_config_history}: append-only, effective-dated settings. A change is a new row; the
 * value in force is the latest row whose {@code effective_from} has passed.
 */
@Entity
@Table(name = "zc_config_history")
@Immutable
public class ConfigHistoryEntry extends AppendOnlyTenantEntity {

    @Column(name = "config_scope", nullable = false)
    private String configScope;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "change_reason", nullable = false)
    private String changeReason;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected ConfigHistoryEntry() {
    }

    public ConfigHistoryEntry(String configScope, String configKey, String configValue, Instant effectiveFrom,
                              String changeReason, String approvedBy, Instant approvedAt) {
        this.configScope = configScope;
        this.configKey = configKey;
        this.configValue = configValue;
        this.effectiveFrom = effectiveFrom;
        this.changeReason = changeReason;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public String getConfigScope() {
        return configScope;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
