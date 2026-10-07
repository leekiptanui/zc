package com.zimasahealth.zcare.domains.carework.controllers;

import com.zimasahealth.zcare.common.api.ApiPaths;
import com.zimasahealth.zcare.common.api.ApiResponse;
import com.zimasahealth.zcare.common.api.AuditOperation;
import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.domains.carework.dto.CompleteTaskRequest;
import com.zimasahealth.zcare.domains.carework.dto.CreateTaskRequest;
import com.zimasahealth.zcare.domains.carework.dto.TaskView;
import com.zimasahealth.zcare.domains.carework.services.TaskOperations;
import com.zimasahealth.zcare.domains.carework.services.TaskService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tasks and work queues (04B sections 10 and 11). */
@RestController
@RequestMapping(ApiPaths.BASE)
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/work-queues/{role}")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN','PROGRAMME_ADMIN','PROVIDER_COORDINATOR','PLATFORM_ADMIN')")
    @AuditOperation(TaskOperations.VIEW_WORK_QUEUE)
    public ApiResponse<PagedResponse<TaskView>> workQueue(@PathVariable String role,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer pageSize) {
        PagedResponse<TaskView> queue = tasks.queue(role, PageParams.of(page, pageSize));
        return ApiResponse.success(queue).nextActions(queue.nextActions());
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','PROGRAMME_ADMIN')")
    @AuditOperation(TaskOperations.ASSIGN_TASK)
    public ApiResponse<TaskView> create(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.from(tasks.create(request));
    }

    @PostMapping("/tasks/{taskId}:complete")
    @PreAuthorize("hasAnyRole('CARE_MANAGER','CLINICIAN')")
    @AuditOperation(TaskOperations.COMPLETE_TASK)
    public ApiResponse<TaskView> complete(@PathVariable long taskId,
                                          @Valid @RequestBody(required = false) CompleteTaskRequest request) {
        return ApiResponse.from(tasks.complete(taskId, request));
    }
}
