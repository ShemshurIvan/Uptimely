package com.project.uptimely.uptimely1.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Test
    void isDue_whenNeverChecked() {
        assertThat(monitor(60, null).isDue(NOW)).isTrue();
    }

    @Test
    void isNotDue_beforeIntervalPassed() {
        assertThat(monitor(60, NOW.minusSeconds(59)).isDue(NOW)).isFalse();
    }

    @Test
    void isDue_exactlyWhenIntervalPassed() {
        assertThat(monitor(60, NOW.minusSeconds(60)).isDue(NOW)).isTrue();
    }

    @Test
    void isDue_whenOverdue() {
        assertThat(monitor(60, NOW.minusSeconds(600)).isDue(NOW)).isTrue();
    }

    private static Monitor monitor(int intervalSeconds, Instant lastCheckedAt) {
        Monitor monitor = new Monitor();
        monitor.setIntervalSeconds(intervalSeconds);
        monitor.setLastCheckedAt(lastCheckedAt);
        return monitor;
    }
}