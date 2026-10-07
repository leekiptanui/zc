package com.zimasahealth.zcare.domains.carework.repositories;

import java.util.List;

import com.zimasahealth.zcare.domains.carework.entities.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    /** Prerequisites of the task that are neither completed nor cancelled. */
    @Query("""
            SELECT t.id FROM TaskDependency d, Task t
             WHERE d.taskId = :taskId AND d.removedAt IS NULL AND t.id = d.dependsOnTaskId
               AND t.status NOT IN ('completed', 'cancelled')
            """)
    List<Long> findOpenPrerequisites(Long taskId);
}
