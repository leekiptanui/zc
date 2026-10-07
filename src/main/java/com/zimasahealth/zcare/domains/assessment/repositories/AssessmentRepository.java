package com.zimasahealth.zcare.domains.assessment.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.assessment.entities.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Optional<Assessment> findFirstByEnrolmentIdAndTemplateVersionIdAndStatusIn(Long enrolmentId,
                                                                               Long templateVersionId,
                                                                               Collection<String> statuses);

    List<Assessment> findByEnrolmentIdAndStatusInOrderByAssignedAtDesc(Long enrolmentId, Collection<String> statuses);
}
