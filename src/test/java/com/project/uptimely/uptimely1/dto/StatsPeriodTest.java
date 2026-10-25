package com.project.uptimely.uptimely1.dto;

import com.project.uptimely.uptimely1.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatsPeriodTest {

    @Test
    void fromValue_findsEachPeriod() {
        assertThat(StatsPeriod.fromValue("24h")).isEqualTo(StatsPeriod.LAST_24H);
        assertThat(StatsPeriod.fromValue("7d")).isEqualTo(StatsPeriod.LAST_7D);
        assertThat(StatsPeriod.fromValue("30d")).isEqualTo(StatsPeriod.LAST_30D);
    }

    @Test
    void fromValue_ignoresCase() {
        assertThat(StatsPeriod.fromValue("7D")).isEqualTo(StatsPeriod.LAST_7D);
    }

    @Test
    void fromValue_throws_whenUnknown() {
        assertThatThrownBy(() -> StatsPeriod.fromValue("1y"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("'1y'");
    }

    @Test
    void fromValue_throws_whenNull() {
        assertThatThrownBy(() -> StatsPeriod.fromValue(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void durations_matchTheirNames() {
        assertThat(StatsPeriod.LAST_24H.getDuration()).isEqualTo(Duration.ofHours(24));
        assertThat(StatsPeriod.LAST_7D.getDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(StatsPeriod.LAST_30D.getDuration()).isEqualTo(Duration.ofDays(30));
    }
}