package com.project.uptimely.uptimely1.dto;

import com.project.uptimely.uptimely1.exception.InvalidRequestException;

import java.time.Duration;

public enum StatsPeriod {
    LAST_24H("24h", Duration.ofHours(24)),
    LAST_7D("7d", Duration.ofDays(7)),
    LAST_30D("30d", Duration.ofDays(30));

    private final String value;
    private final Duration duration;

    StatsPeriod(String value, Duration duration) {
        this.value = value;
        this.duration = duration;
    }

    public String getValue() {
        return value;
    }

    public Duration getDuration() {
        return duration;
    }

    public static StatsPeriod fromValue(String value) {
        for (StatsPeriod period : values()) {
            if (period.value.equalsIgnoreCase(value)) {
                return period;
            }
        }
        throw new InvalidRequestException("Unknown period '" + value + "', expected one of: 24h, 7d, 30d");
    }
}