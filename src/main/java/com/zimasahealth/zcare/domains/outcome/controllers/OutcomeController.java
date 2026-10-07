package com.zimasahealth.zcare.domains.outcome.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.domains.outcome.dto.OutcomeView;
import com.zimasahealth.zcare.domains.outcome.dto.ProgrammeProof;
import com.zimasahealth.zcare.domains.outcome.dto.RecordOutcomeRequest;
import com.zimasahealth.zcare.domains.outcome.services.OutcomeOperations;
import com.zimasahealth.zcare.domains.outcome.services.OutcomeService;
import com.zimasahealth.zcare.domains.outcome.services.ProofService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Outcomes and aggregate programme proof (04B sections 10 and 11). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class OutcomeController {

    private final OutcomeService outcomes;
    private final ProofService proofs;

    public OutcomeController(OutcomeService outcomes, ProofService proofs) {
        this.outcomes = outcomes;
        this.proofs = proofs;
    }

    @PostMapping("/outcomes:record")
    @PreAuthorize("hasRole('PROGRAMME_ADMIN')")
    @AuditOperation(OutcomeOperations.RECORD_OUTCOME)
    public ApiResponse<OutcomeView> record(@Valid @RequestBody RecordOutcomeRequest request) {
        return ApiResponse.from(outcomes.record(request));
    }

    /** @param audience {@code internal}, {@code payer} or {@code employer_aggregate}, as the caller's role allows */
    @GetMapping("/programmes/{programmeId}/proof")
    @PreAuthorize("hasAnyRole('PROGRAMME_ADMIN','PAYER_MANAGER','EMPLOYER_SPONSOR')")
    @AuditOperation(OutcomeOperations.VIEW_PROGRAMME_PROOF)
    public ApiResponse<ProgrammeProof> proof(@PathVariable long programmeId, @RequestParam String audience) {
        return ApiResponse.from(proofs.proof(programmeId, audience));
    }
}
