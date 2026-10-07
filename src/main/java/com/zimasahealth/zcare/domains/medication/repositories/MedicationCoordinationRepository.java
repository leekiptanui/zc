package com.zimasahealth.zcare.domains.medication.repositories;

import com.zimasahealth.zcare.domains.medication.entities.MedicationCoordination;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationCoordinationRepository extends JpaRepository<MedicationCoordination, Long> {
}
