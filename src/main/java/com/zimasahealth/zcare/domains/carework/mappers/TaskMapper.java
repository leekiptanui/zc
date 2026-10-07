package com.zimasahealth.zcare.domains.carework.mappers;

import com.zimasahealth.zcare.domains.carework.dto.TaskView;
import com.zimasahealth.zcare.domains.carework.entities.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface TaskMapper {

    @Mapping(target = "overdue", expression = "java(task.isOpen() && task.getDueAt() != null"
            + " && task.getDueAt().isBefore(java.time.Instant.now()))")
    TaskView toView(Task task);
}
