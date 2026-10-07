package com.zimasahealth.zcare.domains.caregap.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.domains.caregap.dto.CareGapActionRequest;
import com.zimasahealth.zcare.domains.caregap.dto.CareGapView;
import com.zimasahealth.zcare.domains.caregap.dto.RecordCareGapRequest;
import com.zimasahealth.zcare.domains.caregap.services.CareGapOperations;
import com.zimasahealth.zcare.domains.caregap.services.CareGapService;
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
 * Care gaps (04B section 10). Suppress and close admit the whole care team at the filter so that
 * a refused role sees the visible ZCARE_GAP_ACTION_RESTRICTED escalation, not a bare 403.
 */
@RestController
@RequestMapping(ApiPaths.BASE)
public class CareGapController {

    private final CareGapService gaps;

    public CareGapController(CareGapService gaps) {
        this.gaps = gaps;
    }

    @GetMapping("/care-gaps")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROGRAMME_ADMIN')")
    @AuditOperation(CareGapOperations.VIEW_CARE_GAPS)
    public ApiResponse<PagedResponse<CareGapView>> list(
            @RequestParam(name = "filter[status]", required = false) String status,
            @RequestParam(name = "filter[enrolmentId]", required = false) Long enrolmentId,
            @RequestParam(name = "filter[gapType]", required = false) String gapType,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        PagedResponse<CareGapView> result = gaps.list(status, enrolmentId, gapType, PageParams.of(page, pageSize));
        return ApiResponse.success(result).nextActions(result.nextActions());
    }

    @PostMapping("/care-gaps")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','PROGRAMME_ADMIN')")
    @AuditOperation(CareGapOperations.DETECT_CARE_GAP)
    public ApiResponse<CareGapView> record(@Valid @RequestBody RecordCareGapRequest request) {
        return ApiResponse.from(gaps.record(request));
    }

    @PostMapping("/care-gaps/{careGapId}:suppress")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROGRAMME_ADMIN')")
    @AuditOperation(CareGapOperations.SUPPRESS_CARE_GAP)
    public ApiResponse<CareGapView> suppress(@PathVariable long careGapId,
                                             @Valid @RequestBody(required = false) CareGapActionRequest request) {
        return ApiResponse.from(gaps.suppress(careGapId, request));
    }

    @PostMapping("/care-gaps/{careGapId}:close")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROGRAMME_ADMIN')")
    @AuditOperation(CareGapOperations.CLOSE_CARE_GAP)
    public ApiResponse<CareGapView> close(@PathVariable long careGapId,
                                          @Valid @RequestBody(required = false) CareGapActionRequest request) {
        return ApiResponse.from(gaps.close(careGapId, request));
    }
}
