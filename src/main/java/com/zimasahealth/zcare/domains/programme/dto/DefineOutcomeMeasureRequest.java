package com.zimasahealth.zcare.domains.programme.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DefineOutcomeMeasureRequest(
        @NotBlank @Size(max = 64) String measureCode,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 64) String category,
        @NotNull Map<String, Object> definition) {
}
