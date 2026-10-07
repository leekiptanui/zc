package com.zimasahealth.zcare.domains.enrolment.repositories;

import java.util.Collection;
import java.util.Optional;

import com.zimasahealth.zcare.domains.enrolment.entities.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    /** The record in force; at most one exists ({@code uq_zc_consent_record_in_force}). */
    Optional<ConsentRecord> findFirstByEnrolmentIdAndStatusIn(Long enrolmentId, Collection<String> statuses);

    Optional<ConsentRecord> findFirstByEnrolmentIdOrderByCapturedAtDescIdDesc(Long enrolmentId);

    /** Enrolments of a programme whose consent in force is validated. */
    @Query("""
            SELECT count(DISTINCT c.enrolmentId) FROM ConsentRecord c
             WHERE c.status IN ('validated', 'scope_reduced')
               AND c.enrolmentId IN (SELECT e.id FROM Enrolment e WHERE e.programmeId = :programmeId)
            """)
    long countConsentedInProgramme(Long programmeId);
}
