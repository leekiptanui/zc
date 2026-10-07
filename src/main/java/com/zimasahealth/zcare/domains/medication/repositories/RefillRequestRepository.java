package com.zimasahealth.zcare.domains.medication.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.medication.entities.RefillRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefillRequestRepository extends JpaRepository<RefillRequest, Long> {

    Optional<RefillRequest> findFirstByMedicationCoordinationIdAndStatusIn(Long medicationCoordinationId,
                                                                          Collection<String> statuses);

    List<RefillRequest> findByEnrolmentIdAndStatusIn(Long enrolmentId, Collection<String> statuses);
}
