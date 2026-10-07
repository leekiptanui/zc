package com.zimasahealth.zcare.domains.audit.repositories;

import com.zimasahealth.zcare.domains.audit.entities.DomainAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DomainAuditRepository extends JpaRepository<DomainAuditRecord, Long>,
        JpaSpecificationExecutor<DomainAuditRecord> {
}
