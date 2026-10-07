package com.zimasahealth.zcare.domains.enrolment.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.domains.enrolment.dto.ActivateEnrolmentRequest;
import com.zimasahealth.zcare.domains.enrolment.dto.CaptureConsentRequest;
import com.zimasahealth.zcare.domains.enrolment.dto.ConsentView;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.dto.InviteMemberRequest;
import com.zimasahealth.zcare.domains.enrolment.dto.MemberCareContext;
import com.zimasahealth.zcare.domains.enrolment.dto.SuspendEnrolmentRequest;
import com.zimasahealth.zcare.domains.enrolment.dto.WithdrawEnrolmentRequest;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentOperations;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentService;
import com.zimasahealth.zcare.domains.enrolment.services.MemberCareContextService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Enrolment, consent and the member's care context (04B sections 10 and 11). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class EnrolmentController {

    private final EnrolmentService enrolments;
    private final EnrolmentQueryService queries;
    private final MemberCareContextService careContext;

    public EnrolmentController(EnrolmentService enrolments, EnrolmentQueryService queries,
                               MemberCareContextService careContext) {
        this.enrolments = enrolments;
        this.queries = queries;
        this.careContext = careContext;
    }

    @GetMapping("/enrolments")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROGRAMME_ADMIN')")
    @AuditOperation(EnrolmentOperations.VIEW_ENROLMENTS)
    public ApiResponse<PagedResponse<EnrolmentView>> list(
            @RequestParam(name = "filter[status]", required = false) String status,
            @RequestParam(name = "filter[programmeId]", required = false) Long programmeId,
            @RequestParam(name = "filter[memberId]", required = false) Long memberId,
            @RequestParam(name = "filter[responsibleCm]", required = false) String responsibleCm,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        PagedResponse<EnrolmentView> result = queries.list(status, programmeId, memberId, responsibleCm,
                PageParams.of(page, pageSize));
        return ApiResponse.success(result).nextActions(result.nextActions());
    }

    @GetMapping("/enrolments/{enrolmentId}")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(EnrolmentOperations.VIEW_ENROLMENT)
    public ApiResponse<EnrolmentView> get(@PathVariable long enrolmentId) {
        return ApiResponse.success(queries.require(enrolmentId, "enrolmentId")).nextActions("proceed")
                .session("enrolmentId", enrolmentId);
    }

    @PostMapping("/enrolments:invite")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(EnrolmentOperations.INVITE_MEMBER)
    public ApiResponse<EnrolmentView> invite(@Valid @RequestBody InviteMemberRequest request) {
        return ApiResponse.from(enrolments.invite(request.cohortMembershipId()));
    }

    @PostMapping("/enrolments/{enrolmentId}/consent:capture")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(EnrolmentOperations.CAPTURE_CONSENT)
    public ApiResponse<ConsentView> captureConsent(@PathVariable long enrolmentId,
                                                   @Valid @RequestBody CaptureConsentRequest request) {
        return ApiResponse.from(enrolments.captureConsent(enrolmentId, request));
    }

    @PostMapping("/enrolments/{enrolmentId}:activate")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(EnrolmentOperations.ACTIVATE_ENROLMENT)
    public ApiResponse<EnrolmentView> activate(@PathVariable long enrolmentId,
                                               @Valid @RequestBody ActivateEnrolmentRequest request) {
        return ApiResponse.from(enrolments.activate(enrolmentId, request));
    }

    @PostMapping("/enrolments/{enrolmentId}:suspend")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(EnrolmentOperations.SUSPEND_ENROLMENT)
    public ApiResponse<EnrolmentView> suspend(@PathVariable long enrolmentId,
                                              @Valid @RequestBody SuspendEnrolmentRequest request) {
        return ApiResponse.from(enrolments.suspend(enrolmentId, request));
    }

    @PostMapping("/enrolments/{enrolmentId}:resume")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(EnrolmentOperations.RESUME_ENROLMENT)
    public ApiResponse<EnrolmentView> resume(@PathVariable long enrolmentId) {
        return ApiResponse.from(enrolments.resume(enrolmentId));
    }

    @PostMapping("/enrolments/{enrolmentId}:withdraw")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(EnrolmentOperations.WITHDRAW_ENROLMENT)
    public ApiResponse<EnrolmentView> withdraw(@PathVariable long enrolmentId,
                                               @Valid @RequestBody WithdrawEnrolmentRequest request) {
        return ApiResponse.from(enrolments.withdraw(enrolmentId, request));
    }

    @GetMapping("/members/{memberId}/care-context")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(EnrolmentOperations.VIEW_MEMBER_CONTEXT)
    public ApiResponse<MemberCareContext> careContext(@PathVariable long memberId,
                                                      @RequestParam(required = false) Long enrolmentId) {
        return ApiResponse.from(careContext.read(memberId, enrolmentId));
    }
}
