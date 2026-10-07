package com.zimasahealth.zcare.domains.programme.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.domains.programme.dto.AddGoalTypeRequest;
import com.zimasahealth.zcare.domains.programme.dto.AddObservationTypeRequest;
import com.zimasahealth.zcare.domains.programme.dto.CreateProgrammeRequest;
import com.zimasahealth.zcare.domains.programme.dto.DefineOutcomeMeasureRequest;
import com.zimasahealth.zcare.domains.programme.dto.GapRuleView;
import com.zimasahealth.zcare.domains.programme.dto.GoalTypeView;
import com.zimasahealth.zcare.domains.programme.dto.ObservationTypeView;
import com.zimasahealth.zcare.domains.programme.dto.OutcomeDefinitionView;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeDetail;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeSummary;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionState;
import com.zimasahealth.zcare.domains.programme.dto.SetGapRuleRequest;
import com.zimasahealth.zcare.domains.programme.dto.SetTaskTemplateRequest;
import com.zimasahealth.zcare.domains.programme.dto.SetThresholdRequest;
import com.zimasahealth.zcare.domains.programme.dto.TaskTemplateView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeOperations;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Programme authoring and publication (04B section 10; draft authoring endpoints per OD-16, the
 * clinician's approval per ADD Part E and OD-17). Authoring is the programme administrator's;
 * clinical approval is the clinician's alone.
 */
@RestController
@RequestMapping(ApiPaths.BASE)
public class ProgrammeController {

    private final ProgrammeService programmes;
    private final ProgrammeQueryService queries;

    public ProgrammeController(ProgrammeService programmes, ProgrammeQueryService queries) {
        this.programmes = programmes;
        this.queries = queries;
    }

    @GetMapping("/programmes")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','CLINICIAN')")
    @AuditOperation(ProgrammeOperations.VIEW_PROGRAMMES)
    public ApiResponse<PagedResponse<ProgrammeSummary>> list(
            @RequestParam(name = "filter[status]", required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        PagedResponse<ProgrammeSummary> result = queries.list(status, PageParams.of(page, pageSize));
        return ApiResponse.success(result).nextActions(result.nextActions());
    }

    @GetMapping("/programmes/{programmeId}")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','CLINICIAN')")
    @AuditOperation(ProgrammeOperations.VIEW_PROGRAMME)
    public ApiResponse<ProgrammeDetail> detail(@PathVariable long programmeId) {
        return ApiResponse.success(queries.detail(programmeId)).nextActions("proceed")
                .session("programmeId", programmeId);
    }

    @PostMapping("/programmes")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.CREATE_PROGRAMME)
    public ApiResponse<ProgrammeVersionState> create(@Valid @RequestBody CreateProgrammeRequest request) {
        return ApiResponse.from(programmes.create(request));
    }

    @PostMapping("/programmes/{programmeId}/versions")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.CREATE_PROGRAMME_VERSION)
    public ApiResponse<ProgrammeVersionState> createVersion(@PathVariable long programmeId) {
        return ApiResponse.from(programmes.createVersion(programmeId));
    }

    @PostMapping("/programmes/{programmeId}/observation-types")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.ADD_OBSERVATION_TYPE)
    public ApiResponse<ObservationTypeView> addObservationType(@PathVariable long programmeId,
                                                               @Valid @RequestBody AddObservationTypeRequest request) {
        return ApiResponse.from(programmes.addObservationType(programmeId, request));
    }

    @PostMapping("/programmes/{programmeId}/thresholds")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.SET_OBSERVATION_THRESHOLD)
    public ApiResponse<ProgrammeVersionState> setThreshold(@PathVariable long programmeId,
                                                           @Valid @RequestBody SetThresholdRequest request) {
        return ApiResponse.from(programmes.setThreshold(programmeId, request));
    }

    @PostMapping("/programmes/{programmeId}/goal-types")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.ADD_GOAL_TYPE)
    public ApiResponse<GoalTypeView> addGoalType(@PathVariable long programmeId,
                                                 @Valid @RequestBody AddGoalTypeRequest request) {
        return ApiResponse.from(programmes.addGoalType(programmeId, request));
    }

    @PostMapping("/programmes/{programmeId}/gap-rules")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.SET_GAP_RULE)
    public ApiResponse<GapRuleView> setGapRule(@PathVariable long programmeId,
                                               @Valid @RequestBody SetGapRuleRequest request) {
        return ApiResponse.from(programmes.setGapRule(programmeId, request));
    }

    @PostMapping("/programmes/{programmeId}/task-templates")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.SET_TASK_TEMPLATE)
    public ApiResponse<TaskTemplateView> setTaskTemplate(@PathVariable long programmeId,
                                                         @Valid @RequestBody SetTaskTemplateRequest request) {
        return ApiResponse.from(programmes.setTaskTemplate(programmeId, request));
    }

    @PostMapping("/programmes/{programmeId}/outcome-measures")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.DEFINE_OUTCOME_MEASURE)
    public ApiResponse<OutcomeDefinitionView> defineOutcomeMeasure(
            @PathVariable long programmeId, @Valid @RequestBody DefineOutcomeMeasureRequest request) {
        return ApiResponse.from(programmes.defineOutcomeMeasure(programmeId, request));
    }

    @PostMapping("/programmes/{programmeId}:record-clinical-approval")
    @PreAuthorize("hasRole('CLINICIAN')")
    @AuditOperation(ProgrammeOperations.RECORD_CLINICAL_APPROVAL)
    public ApiResponse<ProgrammeVersionState> recordClinicalApproval(@PathVariable long programmeId) {
        return ApiResponse.from(programmes.recordClinicalApproval(programmeId));
    }

    @PostMapping("/programmes/{programmeId}/publish")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.PUBLISH_PROGRAMME)
    public ApiResponse<ProgrammeVersionState> publish(@PathVariable long programmeId) {
        return ApiResponse.from(programmes.publish(programmeId));
    }

    @PostMapping("/programmes/{programmeId}:retire")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.RETIRE_PROGRAMME)
    public ApiResponse<ProgrammeVersionState> retire(@PathVariable long programmeId) {
        return ApiResponse.from(programmes.retire(programmeId));
    }
}
