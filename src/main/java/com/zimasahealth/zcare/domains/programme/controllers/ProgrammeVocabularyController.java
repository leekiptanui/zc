package com.zimasahealth.zcare.domains.programme.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.CollectionResponse;
import com.zimasahealth.zcare.domains.programme.dto.AssessmentTemplateSummary;
import com.zimasahealth.zcare.domains.programme.dto.AssessmentTemplateVersionView;
import com.zimasahealth.zcare.domains.programme.dto.CreateAssessmentTemplateRequest;
import com.zimasahealth.zcare.domains.programme.dto.CreateReferralTypeRequest;
import com.zimasahealth.zcare.domains.programme.dto.ReferralTypeView;
import com.zimasahealth.zcare.domains.programme.services.AssessmentTemplateService;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeOperations;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import com.zimasahealth.zcare.domains.programme.services.ReferralTypeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant vocabulary: referral types and questionnaires (OD-16: to be added to 04B). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class ProgrammeVocabularyController {

    private final ReferralTypeService referralTypes;
    private final AssessmentTemplateService templates;
    private final ProgrammeQueryService queries;

    public ProgrammeVocabularyController(ReferralTypeService referralTypes, AssessmentTemplateService templates,
                                         ProgrammeQueryService queries) {
        this.referralTypes = referralTypes;
        this.templates = templates;
        this.queries = queries;
    }

    @GetMapping("/referral-types")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','CARE_MANAGER','CLINICIAN')")
    @AuditOperation(ProgrammeOperations.VIEW_REFERRAL_TYPES)
    public ApiResponse<CollectionResponse<ReferralTypeView>> listReferralTypes() {
        return ApiResponse.success(CollectionResponse.of(queries.referralTypes())).nextActions("proceed");
    }

    @PostMapping("/referral-types")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.CREATE_REFERRAL_TYPE)
    public ApiResponse<ReferralTypeView> createReferralType(@Valid @RequestBody CreateReferralTypeRequest request) {
        return ApiResponse.from(referralTypes.create(request));
    }

    @GetMapping("/assessment-templates")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','CLINICIAN','CARE_MANAGER')")
    @AuditOperation(ProgrammeOperations.VIEW_ASSESSMENT_TEMPLATES)
    public ApiResponse<CollectionResponse<AssessmentTemplateSummary>> listAssessmentTemplates() {
        return ApiResponse.success(CollectionResponse.of(templates.list())).nextActions("proceed");
    }

    @PostMapping("/assessment-templates")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.CREATE_ASSESSMENT_TEMPLATE)
    public ApiResponse<AssessmentTemplateVersionView> createAssessmentTemplate(
            @Valid @RequestBody CreateAssessmentTemplateRequest request) {
        return ApiResponse.from(templates.create(request));
    }

    @PostMapping("/assessment-templates/{templateId}/publish")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ProgrammeOperations.PUBLISH_ASSESSMENT_TEMPLATE)
    public ApiResponse<AssessmentTemplateVersionView> publishAssessmentTemplate(@PathVariable long templateId) {
        return ApiResponse.from(templates.publish(templateId));
    }
}
