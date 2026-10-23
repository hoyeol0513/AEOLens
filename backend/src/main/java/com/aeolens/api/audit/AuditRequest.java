package com.aeolens.api.audit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuditRequest(
        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL must be 2048 characters or fewer")
        String url
) {
}
