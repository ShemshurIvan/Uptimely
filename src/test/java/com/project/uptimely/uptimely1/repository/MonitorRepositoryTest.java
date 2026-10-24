package com.project.uptimely.uptimely1.repository;

import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MonitorRepositoryTest {

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_fillsIdCreatedAtAndDefaults() {
        Monitor saved = monitorRepository.save(monitor("Google"));
        entityManager.flush();
        entityManager.clear();

        Monitor loaded = monitorRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getActive()).isTrue();
        assertThat(loaded.getCurrentStatus()).isEqualTo(MonitorStatus.UNKNOWN);
        assertThat(loaded.getLastCheckedAt()).isNull();
    }

    @Test
    void checkResults_canBeSavedForSuccessAndFailure() {
        Monitor monitor = monitorRepository.save(monitor("Google"));

        CheckResult success = checkResult(monitor, true, 200, 120L, null);
        // a timeout has no status code and no response time
        CheckResult timeout = checkResult(monitor, false, null, null, "Timeout after 10s");
        checkResultRepository.saveAll(List.of(success, timeout));
        entityManager.flush();
        entityManager.clear();

        List<CheckResult> results = checkResultRepository.findAll();

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> assertThat(r.getMonitor().getId()).isEqualTo(monitor.getId()));
        assertThat(results).anySatisfy(r -> {
            assertThat(r.getUp()).isTrue();
            assertThat(r.getStatusCode()).isEqualTo(200);
            assertThat(r.getErrorMessage()).isNull();
        });
        assertThat(results).anySatisfy(r -> {
            assertThat(r.getUp()).isFalse();
            assertThat(r.getStatusCode()).isNull();
            assertThat(r.getResponseTimeMs()).isNull();
            assertThat(r.getErrorMessage()).isEqualTo("Timeout after 10s");
        });
    }

    @Test
    void url_canBeLongerThan255Characters() {
        Monitor monitor = monitor("Long URL");
        monitor.setUrl("https://example.com/" + "a".repeat(1000));

        Monitor saved = monitorRepository.save(monitor);
        entityManager.flush();
        entityManager.clear();

        assertThat(monitorRepository.findById(saved.getId()).orElseThrow().getUrl()).hasSize(1020);
    }

    @Test
    void findByActiveTrue_skipsPausedMonitors() {
        Monitor active = monitorRepository.save(monitor("Active"));
        Monitor paused = monitor("Paused");
        paused.setActive(false);
        monitorRepository.save(paused);

        assertThat(monitorRepository.findByActiveTrue())
                .extracting(Monitor::getId)
                .containsExactly(active.getId());
    }

    @Test
    void updateStatus_changesOnlyStatusAndLastCheckedAt() {
        Monitor saved = monitorRepository.save(monitor("Google"));
        entityManager.flush();
        entityManager.clear();

        // the user pauses the monitor while a check is in progress
        Monitor fromApi = monitorRepository.findById(saved.getId()).orElseThrow();
        fromApi.setActive(false);
        entityManager.flush();
        entityManager.clear();

        // then the scheduler records the result
        Instant checkedAt = Instant.parse("2026-10-24T12:00:00Z");
        int updated = monitorRepository.updateStatus(saved.getId(), MonitorStatus.UP, checkedAt);
        entityManager.clear();

        Monitor loaded = monitorRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(loaded.getCurrentStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(loaded.getLastCheckedAt()).isEqualTo(checkedAt);
        // the pause survived: no lost update
        assertThat(loaded.getActive()).isFalse();
    }

    @Test
    void updateStatus_returnsZero_whenMonitorDoesNotExist() {
        assertThat(monitorRepository.updateStatus(999L, MonitorStatus.UP, Instant.now())).isZero();
    }

    @Test
    void deletingMonitor_deletesItsCheckResults() {
        Monitor monitor = monitorRepository.save(monitor("Google"));
        checkResultRepository.save(checkResult(monitor, true, 200, 100L, null));
        checkResultRepository.save(checkResult(monitor, false, 503, 50L, "HTTP 503"));
        entityManager.flush();
        entityManager.clear();

        monitorRepository.deleteById(monitor.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(monitorRepository.count()).isZero();
        assertThat(checkResultRepository.count()).isZero();
    }

    private static Monitor monitor(String name) {
        Monitor monitor = new Monitor();
        monitor.setName(name);
        monitor.setUrl("https://google.com");
        monitor.setIntervalSeconds(60);
        return monitor;
    }

    private static CheckResult checkResult(Monitor monitor, boolean up, Integer statusCode,
                                           Long responseTimeMs, String errorMessage) {
        CheckResult result = new CheckResult();
        result.setMonitor(monitor);
        result.setCheckedAt(Instant.now());
        result.setUp(up);
        result.setStatusCode(statusCode);
        result.setResponseTimeMs(responseTimeMs);
        result.setErrorMessage(errorMessage);
        return result;
    }
}