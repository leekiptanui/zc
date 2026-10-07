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

/** {@code zc_outcome_definition}: a versioned outcome measure; every reported figure names its version. */
@Entity
@Table(name = "zc_outcome_definition")
@DynamicUpdate
public class OutcomeDefinition extends TenantEntity {

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "measure_code", nullable = false, updatable = false)
    private String measureCode;

    @Column(name = "measure_version", nullable = false, updatable = false)
    private Integer measureVersion;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false)
    private Map<String, Object> definition;

    protected OutcomeDefinition() {
    }

    public OutcomeDefinition(Long programmeVersionId, String measureCode, Integer measureVersion, String name,
                             String category, Map<String, Object> definition) {
        this.programmeVersionId = programmeVersionId;
        this.measureCode = measureCode;
        this.measureVersion = measureVersion;
        this.name = name;
        this.category = category;
        this.definition = new LinkedHashMap<>(definition);
    }

    public OutcomeDefinition copyTo(Long versionId) {
        return new OutcomeDefinition(versionId, measureCode, measureVersion, name, category, definition);
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public String getMeasureCode() {
        return measureCode;
    }

    public Integer getMeasureVersion() {
        return measureVersion;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }
}
