package com.zimasahealth.zcare.domains.carework.repositories;

import java.util.Collection;
import java.util.List;

import com.zimasahealth.zcare.domains.carework.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findByEnrolmentIdAndStatusIn(Long enrolmentId, Collection<String> statuses);

    List<Task> findByOriginTypeAndOriginIdAndTaskTypeAndStatusIn(String originType, Long originId, String taskType,
                                                                 Collection<String> statuses);
}
