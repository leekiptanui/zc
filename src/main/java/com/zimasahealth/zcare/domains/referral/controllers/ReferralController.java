package com.zimasahealth.zcare.domains.referral.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.domains.referral.dto.CreateReferralRequest;
import com.zimasahealth.zcare.domains.referral.dto.ProviderActionView;
import com.zimasahealth.zcare.domains.referral.dto.ProviderParticipationView;
import com.zimasahealth.zcare.domains.referral.dto.RecordProviderActionRequest;
import com.zimasahealth.zcare.domains.referral.dto.RecordReferralOutcomeRequest;
import com.zimasahealth.zcare.domains.referral.dto.ReferralView;
import com.zimasahealth.zcare.domains.referral.dto.RegisterParticipationRequest;
import com.zimasahealth.zcare.domains.referral.services.ProviderService;
import com.zimasahealth.zcare.domains.referral.services.ReferralOperations;
import com.zimasahealth.zcare.domains.referral.services.ReferralService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Referrals and provider participation (04B section 10; listing and registration per OD-16). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class ReferralController {

    private final ReferralService referrals;
    private final ProviderService providers;

    public ReferralController(ReferralService referrals, ProviderService providers) {
        this.referrals = referrals;
        this.providers = providers;
    }

    @GetMapping("/referrals")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROVIDER_COORDINATOR')")
    @AuditOperation(ReferralOperations.VIEW_REFERRALS)
    public ApiResponse<PagedResponse<ReferralView>> list(
            @RequestParam(name = "filter[status]", required = false) String status,
            @RequestParam(name = "filter[enrolmentId]", required = false) Long enrolmentId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        PagedResponse<ReferralView> result = referrals.list(status, enrolmentId, PageParams.of(page, pageSize));
        return ApiResponse.success(result).nextActions(result.nextActions());
    }

    @PostMapping("/referrals")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(ReferralOperations.CREATE_REFERRAL)
    public ApiResponse<ReferralView> create(@Valid @RequestBody CreateReferralRequest request) {
        return ApiResponse.from(referrals.create(request));
    }

    @PostMapping("/referrals/{referralId}:record-outcome")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROVIDER_COORDINATOR')")
    @AuditOperation(ReferralOperations.RECORD_REFERRAL_OUTCOME)
    public ApiResponse<ReferralView> recordOutcome(@PathVariable long referralId,
                                                   @Valid @RequestBody RecordReferralOutcomeRequest request) {
        return ApiResponse.from(referrals.recordOutcome(referralId, request));
    }

    @PostMapping("/provider-participations")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(ReferralOperations.REGISTER_PROVIDER_PARTICIPATION)
    public ApiResponse<ProviderParticipationView> registerParticipation(
            @Valid @RequestBody RegisterParticipationRequest request) {
        return ApiResponse.from(providers.register(request));
    }

    @PostMapping("/provider-actions")
    @PreAuthorize("hasAnyRole('PROVIDER_COORDINATOR','CLINICIAN')")
    @AuditOperation(ReferralOperations.RECORD_PROVIDER_ACTION)
    public ApiResponse<ProviderActionView> recordProviderAction(
            @Valid @RequestBody RecordProviderActionRequest request) {
        return ApiResponse.from(providers.recordAction(request));
    }
}
