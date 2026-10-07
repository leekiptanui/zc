package com.zimasahealth.zcare.domains.observation.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.domains.observation.dto.CaptureObservationRequest;
import com.zimasahealth.zcare.domains.observation.dto.InvalidateObservationRequest;
import com.zimasahealth.zcare.domains.observation.dto.ObservationResult;
import com.zimasahealth.zcare.domains.observation.dto.ObservationTrend;
import com.zimasahealth.zcare.domains.observation.dto.ObservationView;
import com.zimasahealth.zcare.domains.observation.services.ObservationOperations;
import com.zimasahealth.zcare.domains.observation.services.ObservationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Observations (04B section 10; trend and invalidation per OD-16). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class ObservationController {

    private final ObservationService observations;

    public ObservationController(ObservationService observations) {
        this.observations = observations;
    }

    @PostMapping("/observations")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROVIDER_COORDINATOR')")
    @AuditOperation(ObservationOperations.CAPTURE_OBSERVATION)
    public ApiResponse<ObservationResult> capture(@Valid @RequestBody CaptureObservationRequest request) {
        return ApiResponse.from(observations.capture(request));
    }

    @PostMapping("/observations/{observationId}:invalidate")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(ObservationOperations.INVALIDATE_OBSERVATION)
    public ApiResponse<ObservationView> invalidate(@PathVariable long observationId,
                                                   @Valid @RequestBody InvalidateObservationRequest request) {
        return ApiResponse.from(observations.invalidate(observationId, request.reason()));
    }

    @GetMapping("/enrolments/{enrolmentId}/observations")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(ObservationOperations.VIEW_OBSERVATION_TREND)
    public ApiResponse<ObservationTrend> trend(@PathVariable long enrolmentId,
                                               @RequestParam(name = "type", required = false) String type) {
        return ApiResponse.success(observations.trend(enrolmentId, type)).nextActions("proceed")
                .session("enrolmentId", enrolmentId);
    }
}
