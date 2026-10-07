package com.zimasahealth.zcare.domains.outcome.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code zc_report_snapshot}: append-only, the report exactly as issued. Below-threshold figures
 * are withheld and named, never rounded or approximated.
 */
@Entity
@Table(name = "zc_report_snapshot")
@Immutable
public class ReportSnapshot extends AppendOnlyTenantEntity {

    @Column(name = "programme_version_id", nullable = false)
    private Long programmeVersionId;

    @Column(name = "audience", nullable = false)
    private String audience;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "figures", nullable = false)
    private Map<String, Object> figures;

    @Column(name = "min_population_threshold")
    private Integer minPopulationThreshold;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "withheld_measures", nullable = false)
    private String[] withheldMeasures;

    @Column(name = "attribution_note")
    private String attributionNote;

    @Column(name = "is_incomplete", nullable = false)
    private boolean incomplete;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ReportSnapshot() {
    }

    public ReportSnapshot(Long programmeVersionId, String audience, LocalDate periodStart, LocalDate periodEnd,
                          Map<String, Object> figures, Integer minPopulationThreshold, List<String> withheldMeasures,
                          String attributionNote, Instant generatedAt) {
        this.programmeVersionId = programmeVersionId;
        this.audience = audience;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.figures = new LinkedHashMap<>(figures);
        this.minPopulationThreshold = minPopulationThreshold;
        this.withheldMeasures = withheldMeasures.toArray(String[]::new);
        this.attributionNote = attributionNote;
        this.generatedAt = generatedAt;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public String getAudience() {
        return audience;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Map<String, Object> getFigures() {
        return figures;
    }

    public Integer getMinPopulationThreshold() {
        return minPopulationThreshold;
    }

    public String[] getWithheldMeasures() {
        return withheldMeasures.clone();
    }

    public String getAttributionNote() {
        return attributionNote;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
