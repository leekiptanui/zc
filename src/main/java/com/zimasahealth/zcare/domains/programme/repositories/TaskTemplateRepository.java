package com.zimasahealth.zcare.domains.programme.repositories;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.programme.entities.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, Long> {

    List<TaskTemplate> findByProgrammeVersionIdOrderByTaskType(Long programmeVersionId);

    Optional<TaskTemplate> findByProgrammeVersionIdAndTaskType(Long programmeVersionId, String taskType);
}
