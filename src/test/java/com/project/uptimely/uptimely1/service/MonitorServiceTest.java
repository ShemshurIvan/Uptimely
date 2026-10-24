package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.dto.CheckResultResponse;
import com.project.uptimely.uptimely1.dto.MonitorRequest;
import com.project.uptimely.uptimely1.dto.MonitorResponse;
import com.project.uptimely.uptimely1.dto.UpdateMonitorRequest;
import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import com.project.uptimely.uptimely1.exception.MonitorNotFoundException;
import com.project.uptimely.uptimely1.repository.CheckResultRepository;
import com.project.uptimely.uptimely1.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
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
    void checkNow_throwsNotFound_andChecksNothing_whenMissing() {
        when(monitorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitorService.checkNow(99L))
                .isInstanceOf(MonitorNotFoundException.class);
        verify(healthCheckService, never()).check(any());
        verify(checkResultRepository, never()).save(any());
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