package com.zimasahealth.zcare.domains.audit.services;

import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.domains.audit.dto.AuditQuery;
import com.zimasahealth.zcare.domains.audit.dto.AuditRecordView;
import com.zimasahealth.zcare.domains.audit.entities.DomainAuditRecord;
import com.zimasahealth.zcare.domains.audit.mappers.AuditMapper;
import com.zimasahealth.zcare.domains.audit.repositories.DomainAuditRepository;
import com.zimasahealth.zcare.domains.audit.specifications.AuditSpecifications;
import com.zimasahealth.zcare.security.SecurityEventType;
import com.zimasahealth.zcare.security.SecurityEventWriter;
import com.zimasahealth.zcare.tenant.TenantContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit query (04B section 11; US-ZPA-03): metadata scope only, filterable by who, whom,
 * what, the correlation thread and when. Every export is itself recorded as an
 * {@code audit_export} security event.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final DomainAuditRepository records;
    private final AuditMapper mapper;
    private final SecurityEventWriter securityEvents;

    public AuditQueryService(DomainAuditRepository records, AuditMapper mapper, SecurityEventWriter securityEvents) {
        this.records = records;
        this.mapper = mapper;
        this.securityEvents = securityEvents;
    }

    public PagedResponse<AuditRecordView> query(AuditQuery query, PageParams params) {
        Specification<DomainAuditRecord> filter = Specification
                .where(AuditSpecifications.equalTo("memberId", query.memberId()))
                .and(AuditSpecifications.equalTo("actorId", query.actorId()))
                .and(AuditSpecifications.equalTo("correlationId", query.correlationId()))
                .and(AuditSpecifications.equalTo("operation", query.operation()))
                .and(AuditSpecifications.equalTo("entityType", query.entityType()))
                .and(AuditSpecifications.occurredFrom(query.from()))
                .and(AuditSpecifications.occurredBefore(query.to()));
        PagedResponse<AuditRecordView> page = PagedResponse.of(records.findAll(filter,
                params.toPageable(Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")))),
                params, mapper::toView);
        securityEvents.record(SecurityEventType.AUDIT_EXPORT, TenantContext.require().id(),
                CurrentActor.require().id(), null, "audit query " + query + ", " + page.count() + " rows");
        return page;
    }
}
