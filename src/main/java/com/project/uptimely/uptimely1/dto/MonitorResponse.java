package com.project.uptimely.uptimely1.dto;

import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;

import java.time.Instant;

public record MonitorResponse(Long id, String name, String url, Integer intervalSeconds, Boolean active, MonitorStatus currentStatus, Instant createdAt, Instant lastCheckedAt) {

    public static MonitorResponse from(Monitor monitor) {
        return new MonitorResponse(
                monitor.getId(),
                monitor.getName(),
                monitor.getUrl(),
                monitor.getIntervalSeconds(),
                monitor.getActive(),
                monitor.getCurrentStatus(),
                monitor.getCreatedAt(),
                monitor.getLastCheckedAt()
        );
    }
}
