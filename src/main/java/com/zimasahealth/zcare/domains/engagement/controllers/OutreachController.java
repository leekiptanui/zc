package com.zimasahealth.zcare.domains.engagement.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.domains.engagement.dto.CreateOutreachRequest;
import com.zimasahealth.zcare.domains.engagement.dto.OutreachView;
import com.zimasahealth.zcare.domains.engagement.services.EngagementOperations;
import com.zimasahealth.zcare.domains.engagement.services.OutreachService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Outreach requests (04B section 10). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class OutreachController {

    private final OutreachService outreach;

    public OutreachController(OutreachService outreach) {
        this.outreach = outreach;
    }

    @PostMapping("/outreach-requests")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(EngagementOperations.REQUEST_OUTREACH)
    public ApiResponse<OutreachView> request(@Valid @RequestBody CreateOutreachRequest request) {
        return ApiResponse.from(outreach.request(request));
    }
}
