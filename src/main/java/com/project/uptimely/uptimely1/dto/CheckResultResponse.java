package com.project.uptimely.uptimely1.dto;

import com.project.uptimely.uptimely1.entity.CheckResult;

import java.time.Instant;

public record CheckResultResponse(Long id, Instant checkedAt, Boolean up,
                                  Integer statusCode, Long responseTimeMs, String errorMessage) {
    public static CheckResultResponse from(CheckResult checkResult) {
        return new CheckResultResponse(
                checkResult.getId(),
                checkResult.getCheckedAt(),
                checkResult.getUp(),
                checkResult.getStatusCode(),
                checkResult.getResponseTimeMs(),
                checkResult.getErrorMessage()
        );
    }
}
