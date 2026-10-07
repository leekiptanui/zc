package com.zimasahealth.zcare.domains.audit.mappers;

import com.zimasahealth.zcare.domains.audit.dto.AuditRecordView;
import com.zimasahealth.zcare.domains.audit.entities.DomainAuditRecord;
import org.mapstruct.Mapper;

@Mapper
public interface AuditMapper {

    AuditRecordView toView(DomainAuditRecord record);
}
