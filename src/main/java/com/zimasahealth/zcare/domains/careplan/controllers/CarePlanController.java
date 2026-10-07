package com.zimasahealth.zcare.domains.careplan.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanView;
import com.zimasahealth.zcare.domains.careplan.dto.CreateCarePlanRequest;
import com.zimasahealth.zcare.domains.careplan.dto.PlanDecisionRequest;
import com.zimasahealth.zcare.domains.careplan.services.CarePlanOperations;
import com.zimasahealth.zcare.domains.careplan.services.CarePlanService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Care plans (04B section 10; submit per OD-16; request-changes and reject per ADD Part B and
 * OD-17). Activation and the other decisions are clinician-only: anyone else gets 403 here,
 * outside the envelope (04B section 6).
 */
@RestController
@RequestMapping(ApiPaths.BASE)
public class CarePlanController {

    private final CarePlanService plans;

    public CarePlanController(CarePlanService plans) {
        this.plans = plans;
    }

    @GetMapping("/care-plans/{carePlanId}")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(CarePlanOperations.VIEW_CARE_PLAN)
    public ApiResponse<CarePlanView> get(@PathVariable long carePlanId) {
        return ApiResponse.success(plans.get(carePlanId)).nextActions("proceed").session("carePlanId", carePlanId);
    }

    @PostMapping("/care-plans")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(CarePlanOperations.CREATE_CARE_PLAN)
    public ApiResponse<CarePlanView> create(@Valid @RequestBody CreateCarePlanRequest request) {
        return ApiResponse.from(plans.create(request));
    }

    @PostMapping("/care-plans/{carePlanId}:submit")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(CarePlanOperations.SUBMIT_FOR_APPROVAL)
    public ApiResponse<CarePlanView> submit(@PathVariable long carePlanId) {
        return ApiResponse.from(plans.submit(carePlanId));
    }

    @PostMapping("/care-plans/{carePlanId}:activate")
    @PreAuthorize("hasRole('CLINICIAN')")
    @AuditOperation(CarePlanOperations.APPROVE_CARE_PLAN)
    public ApiResponse<CarePlanView> activate(@PathVariable long carePlanId) {
        return ApiResponse.from(plans.activate(carePlanId));
    }

    @PostMapping("/care-plans/{carePlanId}:request-changes")
    @PreAuthorize("hasRole('CLINICIAN')")
    @AuditOperation(CarePlanOperations.REQUEST_PLAN_CHANGES)
    public ApiResponse<CarePlanView> requestChanges(@PathVariable long carePlanId,
                                                    @Valid @RequestBody PlanDecisionRequest request) {
        return ApiResponse.from(plans.requestChanges(carePlanId, request.notes()));
    }

    @PostMapping("/care-plans/{carePlanId}:reject")
    @PreAuthorize("hasRole('CLINICIAN')")
    @AuditOperation(CarePlanOperations.REJECT_CARE_PLAN)
    public ApiResponse<CarePlanView> reject(@PathVariable long carePlanId,
                                            @Valid @RequestBody PlanDecisionRequest request) {
        return ApiResponse.from(plans.reject(carePlanId, request.notes()));
    }

    @PostMapping("/care-plans/{carePlanId}:revise")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(CarePlanOperations.REVISE_CARE_PLAN)
    public ApiResponse<CarePlanView> revise(@PathVariable long carePlanId) {
        return ApiResponse.from(plans.revise(carePlanId));
    }
}
