package com.zimasahealth.zcare.domains.programme.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** @param defaultPriority 1 is most urgent, 5 least; defaults to 3 */
public record SetTaskTemplateRequest(
        @NotBlank @Size(max = 64) String taskType,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Min(1) @Max(5) Integer defaultPriority,
        @Positive Integer slaHours,
        @Size(max = 64) String defaultAssigneeRole) {
}
