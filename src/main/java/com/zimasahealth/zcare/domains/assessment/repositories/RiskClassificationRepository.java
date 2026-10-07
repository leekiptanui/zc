package com.zimasahealth.zcare.domains.assessment.repositories;

import java.util.Optional;

import com.zimasahealth.zcare.domains.assessment.entities.RiskClassification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskClassificationRepository extends JpaRepository<RiskClassification, Long> {

    Optional<RiskClassification> findFirstByEnrolmentIdOrderByClassifiedAtDescIdDesc(Long enrolmentId);
}
