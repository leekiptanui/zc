package com.zimasahealth.zcare.domains.programme.entities;

import java.util.LinkedHashMap;
import java.util.Map;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code zc_gap_rule}: when a care gap is raised. Changing a rule adds a new {@code rule_version}
 * and deactivates the old one, so every gap names the exact rule that raised it.
 */
@Entity
@Table(name = "zc_gap_rule")
@DynamicUpdate
public class GapRule extends TenantEntity {

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "gap_type", nullable = false, updatable = false)
    private String gapType;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private Integer ruleVersion;

    @Column(name = "period_strategy", nullable = false)
    private String periodStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false)
    private Map<String, Object> definition;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected GapRule() {
    }

    public GapRule(Long programmeVersionId, String gapType, Integer ruleVersion, String periodStrategy,
                   Map<String, Object> definition, String description) {
        this.programmeVersionId = programmeVersionId;
        this.gapType = gapType;
        this.ruleVersion = ruleVersion;
        this.periodStrategy = periodStrategy;
        this.definition = new LinkedHashMap<>(definition);
        this.description = description;
    }

    public GapRule copyTo(Long versionId) {
        return new GapRule(versionId, gapType, ruleVersion, periodStrategy, definition, description);
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public String getGapType() {
        return gapType;
    }

    public Integer getRuleVersion() {
        return ruleVersion;
    }

    public String getPeriodStrategy() {
        return periodStrategy;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }
}
