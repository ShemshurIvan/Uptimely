package com.project.uptimely.uptimely1.dto;

import java.time.Instant;

public record MonitorStatsResponse(Long monitorId,
                                    String period,
                                    Instant from,
                                    Instant to,
                                    long totalChecks,
                                    long failedChecks,
                                    Double uptimePercent,
                                    Double avgResponseMs) {
}
