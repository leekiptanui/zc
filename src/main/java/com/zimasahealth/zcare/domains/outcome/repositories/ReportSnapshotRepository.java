package com.zimasahealth.zcare.domains.outcome.repositories;

import com.zimasahealth.zcare.domains.outcome.entities.ReportSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportSnapshotRepository extends JpaRepository<ReportSnapshot, Long> {
}
