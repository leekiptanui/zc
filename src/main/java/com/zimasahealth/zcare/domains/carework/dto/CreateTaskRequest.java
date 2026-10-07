package com.zimasahealth.zcare.domains.carework.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A manual task. It is assigned to a person ({@code assignedToActor}), a role
 * ({@code assignedToRole}), or both.
 *
 * @param priority 1 is most urgent, 5 least; defaults to 3
 */
public record CreateTaskRequest(
        @NotNull Long enrolmentId,
        @NotBlank @Size(max = 64) String taskType,
        @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Min(1) @Max(5) Integer priority,
        Instant dueAt,
        @Size(max = 128) String assignedToActor,
        @Size(max = 64) String assignedToRole) {
}
