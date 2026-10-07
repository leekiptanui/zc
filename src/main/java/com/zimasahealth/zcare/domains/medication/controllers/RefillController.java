package com.zimasahealth.zcare.domains.medication.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.domains.medication.dto.CreateRefillRequest;
import com.zimasahealth.zcare.domains.medication.dto.RecordFulfilmentRequest;
import com.zimasahealth.zcare.domains.medication.dto.RefillView;
import com.zimasahealth.zcare.domains.medication.services.MedicationOperations;
import com.zimasahealth.zcare.domains.medication.services.RefillService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Refill requests (04B section 10; DEC-006 pilot scope). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class RefillController {

    private final RefillService refills;

    public RefillController(RefillService refills) {
        this.refills = refills;
    }

    @PostMapping("/refill-requests")
    @PreAuthorize("hasRole('CARE_MANAGER')")
    @AuditOperation(MedicationOperations.CREATE_REFILL_REQUEST)
    public ApiResponse<RefillView> create(@Valid @RequestBody CreateRefillRequest request) {
        return ApiResponse.from(refills.create(request));
    }

    @PostMapping("/refill-requests/{refillRequestId}:record-fulfilment")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','PROVIDER_COORDINATOR')")
    @AuditOperation(MedicationOperations.RECORD_FULFILMENT)
    public ApiResponse<RefillView> recordFulfilment(@PathVariable long refillRequestId,
                                                    @Valid @RequestBody RecordFulfilmentRequest request) {
        return ApiResponse.from(refills.recordFulfilment(refillRequestId, request));
    }
}
