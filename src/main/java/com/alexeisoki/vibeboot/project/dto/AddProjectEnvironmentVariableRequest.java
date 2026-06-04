package com.alexeisoki.vibeboot.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddProjectEnvironmentVariableRequest(
        @NotBlank
        @Pattern(
                regexp = "[A-Z_][A-Z0-9_]*",
                message = "must match [A-Z_][A-Z0-9_]*"
        )
        String key,
        @NotNull
        String value
) {
}
