package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.AssessmentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentTemplateRepository extends JpaRepository<AssessmentTemplate, Long> {

    Optional<AssessmentTemplate> findByCode(String code);

    boolean existsByCode(String code);
}
