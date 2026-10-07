package com.zimasahealth.zcare.domains.programme.dto;

public record TaskTemplateView(Long id, String taskType, String name, String description, Short defaultPriority,
                               Integer slaHours, String defaultAssigneeRole) {
}
