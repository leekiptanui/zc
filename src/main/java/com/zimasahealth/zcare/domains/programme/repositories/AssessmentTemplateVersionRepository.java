package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.AssessmentTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentTemplateVersionRepository extends JpaRepository<AssessmentTemplateVersion, Long> {

    List<AssessmentTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(Long templateId);

    Optional<AssessmentTemplateVersion> findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(Long templateId,
                                                                                               String status);

    Optional<AssessmentTemplateVersion> findFirstByStatusOrderByPublishedAtDesc(String status);
}
