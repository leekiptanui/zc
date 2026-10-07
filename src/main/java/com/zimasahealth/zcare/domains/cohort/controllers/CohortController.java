package com.zimasahealth.zcare.domains.cohort.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.domains.cohort.dto.AddCohortMemberRequest;
import com.zimasahealth.zcare.domains.cohort.dto.CohortMembershipView;
import com.zimasahealth.zcare.domains.cohort.dto.CohortRunView;
import com.zimasahealth.zcare.domains.cohort.dto.CohortView;
import com.zimasahealth.zcare.domains.cohort.dto.CreateCohortRequest;
import com.zimasahealth.zcare.domains.cohort.services.CohortOperations;
import com.zimasahealth.zcare.domains.cohort.services.CohortService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cohorts (04B section 10; release and member listing per OD-16). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class CohortController {

    private final CohortService cohorts;

    public CohortController(CohortService cohorts) {
        this.cohorts = cohorts;
    }

    @GetMapping("/cohorts")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','CARE_MANAGER')")
    @AuditOperation(CohortOperations.VIEW_COHORTS)
    public ApiResponse<PagedResponse<CohortView>> list(@RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer pageSize) {
        PagedResponse<CohortView> result = cohorts.list(PageParams.of(page, pageSize));
        return ApiResponse.success(result).nextActions(result.nextActions());
    }

    @PostMapping("/cohorts")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(CohortOperations.CREATE_COHORT)
    public ApiResponse<CohortView> create(@Valid @RequestBody CreateCohortRequest request) {
        return ApiResponse.from(cohorts.create(request));
    }

    @GetMapping("/cohorts/{cohortId}/members")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','CARE_MANAGER')")
    @AuditOperation(CohortOperations.VIEW_COHORT_MEMBERS)
    public ApiResponse<PagedResponse<CohortMembershipView>> members(@PathVariable long cohortId,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer pageSize) {
        PagedResponse<CohortMembershipView> result = cohorts.members(cohortId, PageParams.of(page, pageSize));
        return ApiResponse.success(result).nextActions(result.nextActions()).session("cohortId", cohortId);
    }

    @PostMapping("/cohorts/{cohortId}/members")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','CARE_MANAGER')")
    @AuditOperation(CohortOperations.ADD_COHORT_MEMBER)
    public ApiResponse<CohortMembershipView> addMember(@PathVariable long cohortId,
                                                       @Valid @RequestBody AddCohortMemberRequest request) {
        return ApiResponse.from(cohorts.addMember(cohortId, request));
    }

    @PostMapping("/cohorts/{cohortId}:identify")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(CohortOperations.IDENTIFY_COHORT)
    public ApiResponse<CohortRunView> identify(@PathVariable long cohortId) {
        return ApiResponse.from(cohorts.identify(cohortId));
    }

    @PostMapping("/cohorts/{cohortId}:release")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(CohortOperations.RELEASE_COHORT)
    public ApiResponse<CohortView> release(@PathVariable long cohortId) {
        return ApiResponse.from(cohorts.release(cohortId));
    }
}
