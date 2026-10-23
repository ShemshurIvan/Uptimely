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