package com.project.uptimely.uptimely1.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record MonitorRequest(@NotBlank @Size(max = 255) String name,
                             @NotBlank @URL @Size(max = 2048) String url,
                             @NotNull @Min(10) Integer intervalSeconds) {
}
