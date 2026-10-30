package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.dto.CheckResultResponse;
import com.project.uptimely.uptimely1.dto.MonitorRequest;
import com.project.uptimely.uptimely1.dto.MonitorResponse;
import com.project.uptimely.uptimely1.dto.MonitorStatsResponse;
import com.project.uptimely.uptimely1.dto.UpdateMonitorRequest;
import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import com.project.uptimely.uptimely1.exception.InvalidRequestException;
import com.project.uptimely.uptimely1.exception.MonitorNotFoundException;
import com.project.uptimely.uptimely1.repository.CheckResultRepository;
import com.project.uptimely.uptimely1.repository.CheckStats;
import com.project.uptimely.uptimely1.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private HealthCheckService healthCheckService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private MonitorService monitorService;

    @Test
    void create_savesMonitorWithRequestFieldsAndDefaults() {
        when(monitorRepository.save(any(Monitor.class))).thenAnswer(returnsFirstArg());

        MonitorResponse response = monitorService.create(
                new MonitorRequest("Google", "https://google.com", 60));

        ArgumentCaptor<Monitor> captor = ArgumentCaptor.forClass(Monitor.class);
        verify(monitorRepository).save(captor.capture());
        Monitor saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Google");
        assertThat(saved.getUrl()).isEqualTo("https://google.com");
        assertThat(saved.getIntervalSeconds()).isEqualTo(60);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getCurrentStatus()).isEqualTo(MonitorStatus.UNKNOWN);

        assertThat(response.name()).isEqualTo("Google");
        assertThat(response.currentStatus()).isEqualTo(MonitorStatus.UNKNOWN);
    }

    @Test
    void getById_returnsMonitor_whenExists() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));

        MonitorResponse response = monitorService.getById(1L);

        assertThat(response.name()).isEqualTo("Google");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(monitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorService.getById(99L))
                .isInstanceOf(MonitorNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_changesFieldsWithoutCallingSave() {
        Monitor existing = monitor("Old name");
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));

        MonitorResponse response = monitorService.update(1L,
                new UpdateMonitorRequest("New name", "https://new.example.com", 30));

        assertThat(existing.getName()).isEqualTo("New name");
        assertThat(existing.getUrl()).isEqualTo("https://new.example.com");
        assertThat(existing.getIntervalSeconds()).isEqualTo(30);
        assertThat(response.name()).isEqualTo("New name");
        // relies on dirty checking inside @Transactional
        verify(monitorRepository, never()).save(any());
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(monitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorService.update(99L,
                new UpdateMonitorRequest("Name", "https://example.com", 30)))
                .isInstanceOf(MonitorNotFoundException.class);
    }

    @Test
    void pause_setsActiveFalse() {
        Monitor existing = monitor("Google");
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));

        MonitorResponse response = monitorService.pause(1L);

        assertThat(existing.getActive()).isFalse();
        assertThat(response.active()).isFalse();
    }

    @Test
    void pause_isIdempotent() {
        Monitor existing = monitor("Google");
        existing.setActive(false);
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));

        MonitorResponse response = monitorService.pause(1L);

        assertThat(response.active()).isFalse();
    }

    @Test
    void resume_setsActiveTrue() {
        Monitor existing = monitor("Google");
        existing.setActive(false);
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));

        MonitorResponse response = monitorService.resume(1L);

        assertThat(existing.getActive()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    void deleteById_deletesMonitor_whenExists() {
        Monitor existing = monitor("Google");
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));

        monitorService.deleteById(1L);

        verify(monitorRepository).delete(existing);
    }

    @Test
    void deleteById_throwsNotFound_andDeletesNothing_whenMissing() {
        when(monitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorService.deleteById(99L))
                .isInstanceOf(MonitorNotFoundException.class);
        verify(monitorRepository, never()).delete(any(Monitor.class));
    }

    @Test
    void checkNow_savesResultAndMarksMonitorUp() {
        Monitor existing = monitor("Google");
        CheckResult result = checkResult(existing, true, 200);
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(healthCheckService.check(existing)).thenReturn(Mono.just(result));
        when(checkResultRepository.save(result)).thenReturn(result);

        CheckResultResponse response = monitorService.checkNow(1L);

        verify(checkResultRepository).save(result);
        verify(monitorRepository).updateStatus(1L, MonitorStatus.UP, result.getCheckedAt());
        // the whole monitor must not be saved: that would overwrite concurrent changes (lost update)
        verify(monitorRepository, never()).save(any(Monitor.class));
        assertThat(response.up()).isTrue();
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void checkNow_marksMonitorDown_whenCheckFails() {
        Monitor existing = monitor("Broken");
        CheckResult result = checkResult(existing, false, null);
        result.setErrorMessage("Connection refused");
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(healthCheckService.check(existing)).thenReturn(Mono.just(result));
        when(checkResultRepository.save(result)).thenReturn(result);

        CheckResultResponse response = monitorService.checkNow(1L);

        verify(monitorRepository).updateStatus(1L, MonitorStatus.DOWN, result.getCheckedAt());
        assertThat(response.up()).isFalse();
        assertThat(response.errorMessage()).isEqualTo("Connection refused");
    }

    @Test
    void checkNow_passesStatusBeforeCheckToAlerts() {
        Monitor existing = monitor("Google");
        existing.setCurrentStatus(MonitorStatus.UP);
        CheckResult result = checkResult(existing, false, 503);
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(healthCheckService.check(existing)).thenReturn(Mono.just(result));
        when(checkResultRepository.save(result)).thenReturn(result);

        monitorService.checkNow(1L);

        verify(alertService).notifyIfChanged(existing, MonitorStatus.UP, result);
    }

    @Test
    void checkNow_throwsNotFound_andChecksNothing_whenMissing() {
        when(monitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorService.checkNow(99L))
                .isInstanceOf(MonitorNotFoundException.class);
        verify(healthCheckService, never()).check(any());
        verify(checkResultRepository, never()).save(any());
    }

    // --- getChecks ---

    @Test
    void getChecks_defaultsToLast24Hours_whenNoRangeGiven() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));
        when(checkResultRepository.findByMonitorIdAndCheckedAtBetween(eq(1L), any(), any(), any()))
                .thenReturn(Page.empty());

        monitorService.getChecks(1L, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(checkResultRepository).findByMonitorIdAndCheckedAtBetween(
                eq(1L), from.capture(), to.capture(), any());
        assertThat(Duration.between(from.getValue(), to.getValue())).isEqualTo(Duration.ofHours(24));
        assertThat(to.getValue()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void getChecks_usesGivenRangeAndMapsResults() {
        Instant from = Instant.parse("2026-10-24T00:00:00Z");
        Instant to = Instant.parse("2026-10-24T12:00:00Z");
        Monitor monitor = monitor("Google");
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor));
        when(checkResultRepository.findByMonitorIdAndCheckedAtBetween(eq(1L), eq(from), eq(to), any()))
                .thenReturn(new PageImpl<>(List.of(checkResult(monitor, true, 200))));

        Page<CheckResultResponse> page = monitorService.getChecks(1L, from, to, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().statusCode()).isEqualTo(200);
    }

    @Test
    void getChecks_throwsInvalidRequest_whenFromAfterTo() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));

        assertThatThrownBy(() -> monitorService.getChecks(1L,
                Instant.parse("2026-10-24T12:00:00Z"), Instant.parse("2026-10-24T00:00:00Z"),
                PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class);
        verify(checkResultRepository, never()).findByMonitorIdAndCheckedAtBetween(any(), any(), any(), any());
    }

    @Test
    void getChecks_throwsNotFound_whenMonitorMissing() {
        when(monitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorService.getChecks(99L, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(MonitorNotFoundException.class);
    }

    // --- getStats ---

    @Test
    void getStats_calculatesUptimeAndRoundsToTwoDecimals() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));
        when(checkResultRepository.getStats(eq(1L), any())).thenReturn(stats(3, 1L, 123.456));

        MonitorStatsResponse response = monitorService.getStats(1L, "24h");

        assertThat(response.monitorId()).isEqualTo(1L);
        assertThat(response.period()).isEqualTo("24h");
        assertThat(response.totalChecks()).isEqualTo(3);
        assertThat(response.failedChecks()).isEqualTo(1);
        assertThat(response.uptimePercent()).isEqualTo(66.67);
        assertThat(response.avgResponseMs()).isEqualTo(123.46);
    }

    @Test
    void getStats_usesPeriodDuration() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));
        when(checkResultRepository.getStats(eq(1L), any())).thenReturn(stats(0, null, null));

        MonitorStatsResponse response = monitorService.getStats(1L, "7d");

        assertThat(response.period()).isEqualTo("7d");
        assertThat(Duration.between(response.from(), response.to())).isEqualTo(Duration.ofDays(7));
        verify(checkResultRepository).getStats(1L, response.from());
    }

    @Test
    void getStats_returnsNullUptime_whenNoChecks() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));
        when(checkResultRepository.getStats(eq(1L), any())).thenReturn(stats(0, null, null));

        MonitorStatsResponse response = monitorService.getStats(1L, "24h");

        assertThat(response.totalChecks()).isZero();
        assertThat(response.failedChecks()).isZero();
        assertThat(response.uptimePercent()).isNull();
        assertThat(response.avgResponseMs()).isNull();
    }

    @Test
    void getStats_returns100_whenNothingFailed() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));
        when(checkResultRepository.getStats(eq(1L), any())).thenReturn(stats(5, 0L, 80.0));

        assertThat(monitorService.getStats(1L, "24h").uptimePercent()).isEqualTo(100.0);
    }

    @Test
    void getStats_throwsInvalidRequest_whenPeriodUnknown() {
        when(monitorRepository.findById(1L)).thenReturn(Optional.of(monitor("Google")));

        assertThatThrownBy(() -> monitorService.getStats(1L, "1y"))
                .isInstanceOf(InvalidRequestException.class);
        verify(checkResultRepository, never()).getStats(any(), any());
    }

    @Test
    void getStats_throwsNotFound_whenMonitorMissing() {
        when(monitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorService.getStats(99L, "24h"))
                .isInstanceOf(MonitorNotFoundException.class);
    }

    private static CheckStats stats(long total, Long failed, Double avgResponseMs) {
        return new CheckStats() {
            public long getTotal() { return total; }
            public Long getFailed() { return failed; }
            public Double getAvgResponseMs() { return avgResponseMs; }
        };
    }

    private static CheckResult checkResult(Monitor monitor, boolean up, Integer statusCode) {
        CheckResult result = new CheckResult();
        result.setMonitor(monitor);
        result.setCheckedAt(Instant.parse("2026-10-24T10:00:00Z"));
        result.setUp(up);
        result.setStatusCode(statusCode);
        return result;
    }

    private static Monitor monitor(String name) {
        Monitor monitor = new Monitor();
        monitor.setName(name);
        monitor.setUrl("https://google.com");
        monitor.setIntervalSeconds(60);
        // id is generated by the DB, so there is no setter; set it directly for unit tests
        ReflectionTestUtils.setField(monitor, "id", 1L);
        return monitor;
    }
}