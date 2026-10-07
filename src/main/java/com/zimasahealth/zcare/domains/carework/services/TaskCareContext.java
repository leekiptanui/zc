package com.zimasahealth.zcare.domains.carework.services;

import com.zimasahealth.zcare.common.carecontext.CareContextContributor;
import org.springframework.stereotype.Component;

/** The enrolment's open tasks. */
@Component
public class TaskCareContext implements CareContextContributor {

    private final TaskService tasks;

    public TaskCareContext(TaskService tasks) {
        this.tasks = tasks;
    }

    @Override
    public String section() {
        return "openTasks";
    }

    @Override
    public Object contribute(long enrolmentId) {
        return tasks.openForEnrolment(enrolmentId);
    }
}
