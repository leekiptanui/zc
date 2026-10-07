package com.zimasahealth.zcare.domains.observation.dto;

/**
 * @param thresholdEvaluated false when no evaluable threshold is configured: stored, but visibly not checked
 * @param reviewTaskId       the clinician review raised by a breach, if any; a review trigger, never a diagnosis
 */
public record ObservationResult(ObservationView observation, boolean thresholdEvaluated, boolean thresholdBreached,
                                Long reviewTaskId) {
}
