package com.zimasahealth.zcare.domains.outcome.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregate programme proof for one audience. Every figure carries its measure version;
 * withheld measures are named, never estimated.
 *
 * @param reportSnapshotId the frozen copy of exactly this report
 */
public record ProgrammeProof(Long programmeId, String audience, LocalDate asAt, Integer minPopulationThreshold,
                             Map<String, Object> figures, List<String> withheldMeasures, String attributionNote,
                             Long reportSnapshotId) {
}
