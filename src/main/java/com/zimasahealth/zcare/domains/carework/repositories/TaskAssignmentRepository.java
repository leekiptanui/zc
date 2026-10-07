package com.zimasahealth.zcare.domains.carework.repositories;

import com.zimasahealth.zcare.domains.carework.entities.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
}
