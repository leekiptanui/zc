package com.zimasahealth.zcare.domains.audit.controllers;

import java.time.Instant;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.domains.audit.dto.AuditQuery;
import com.zimasahealth.zcare.domains.audit.dto.AuditRecordView;
import com.zimasahealth.zcare.domains.audit.services.AuditOperations;
import com.zimasahealth.zcare.domains.audit.services.AuditQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The audit trail query (04B section 11). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class AuditController {

    private final AuditQueryService audit;

    public AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PROGRAMME_ADMIN')")
    @AuditOperation(AuditOperations.AUDIT_QUERY)
    public ApiResponse<PagedResponse<AuditRecordView>> query(
            @RequestParam(name = "filter[memberId]", required = false) Long memberId,
            @RequestParam(name = "filter[actorId]", required = false) String actorId,
            @RequestParam(name = "filter[correlationId]", required = false) String correlationId,
            @RequestParam(name = "filter[operation]", required = false) String operation,
            @RequestParam(name = "filter[entityType]", required = false) String entityType,
            @RequestParam(name = "filter[from]", required = false) Instant from,
            @RequestParam(name = "filter[to]", required = false) Instant to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        PagedResponse<AuditRecordView> result = audit.query(
                new AuditQuery(memberId, actorId, correlationId, operation, entityType, from, to),
                PageParams.of(page, pageSize));
        return ApiResponse.success(result).nextActions(result.nextActions());
    }
}
