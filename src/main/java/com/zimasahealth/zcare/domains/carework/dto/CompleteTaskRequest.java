package com.zimasahealth.zcare.domains.carework.dto;

import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(@Size(max = 2000) String completionNote) {
}
