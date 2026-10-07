package com.zimasahealth.zcare.domains.assessment.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.domains.assessment.dto.AssessmentResult;
import com.zimasahealth.zcare.domains.assessment.dto.AssessmentView;
import com.zimasahealth.zcare.domains.assessment.dto.AssignAssessmentRequest;
import com.zimasahealth.zcare.domains.assessment.dto.CompleteAssessmentRequest;
import com.zimasahealth.zcare.domains.assessment.services.AssessmentOperations;
import com.zimasahealth.zcare.domains.assessment.services.AssessmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Assessments (04B section 10; assignment per OD-16). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class AssessmentController {

    private final AssessmentService assessments;

    public AssessmentController(AssessmentService assessments) {
        this.assessments = assessments;
    }

    @PostMapping("/enrolments/{enrolmentId}/assessments")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(AssessmentOperations.ASSIGN_ASSESSMENT)
    public ApiResponse<AssessmentView> assign(@PathVariable long enrolmentId,
                                              @Valid @RequestBody(required = false) AssignAssessmentRequest request) {
        return ApiResponse.from(assessments.assign(enrolmentId, request));
    }

    @PostMapping("/assessments/{assessmentId}:complete")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(AssessmentOperations.COMPLETE_ASSESSMENT)
    public ApiResponse<AssessmentResult> complete(@PathVariable long assessmentId,
                                                  @Valid @RequestBody CompleteAssessmentRequest request) {
        return ApiResponse.from(assessments.complete(assessmentId, request));
    }
}
