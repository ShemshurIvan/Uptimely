package com.project.uptimely.uptimely1.repository;

import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CheckResultRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Autowired
    private MonitorRepository monitorRepository;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Autowired
    private TestEntityManager entityManager;

    // --- getStats ---

    @Test
    void getStats_countsTotalAndFailed() {
        Monitor monitor = monitorRepository.save(monitor("Google"));
        save(monitor, true, 100L, NOW.minusSeconds(60));
        save(monitor, true, 200L, NOW.minusSeconds(120));
        save(monitor, true, 300L, NOW.minusSeconds(180));
        save(monitor, false, null, NOW.minusSeconds(240));

        CheckStats stats = checkResultRepository.getStats(monitor.getId(), NOW.minus(Duration.ofHours(1)));

        assertThat(stats.getTotal()).isEqualTo(4);
        assertThat(stats.getFailed()).isEqualTo(1);
    }

    @Test
    void getStats_averagesOnlyUpChecks() {
        Monitor monitor = monitorRepository.save(monitor("Google"));
        save(monitor, true, 100L, NOW.minusSeconds(60));
        save(monitor, true, 200L, NOW.minusSeconds(120));
        // a failed check with a huge time must not pull the average up
        save(monitor, false, 10_000L, NOW.minusSeconds(180));

        CheckStats stats = checkResultRepository.getStats(monitor.getId(), NOW.minus(Duration.ofHours(1)));

        assertThat(stats.getAvgResponseMs()).isEqualTo(150.0);
    }

    @Test
    void getStats_ignoresChecksBeforeFrom() {
        Monitor monitor = monitorRepository.save(monitor("Google"));
        save(monitor, true, 100L, NOW.minusSeconds(60));
        save(monitor, false, null, NOW.minus(Duration.ofDays(2)));

        CheckStats stats = checkResultRepository.getStats(monitor.getId(), NOW.minus(Duration.ofHours(24)));

        assertThat(stats.getTotal()).isEqualTo(1);
        assertThat(stats.getFailed()).isZero();
    }

    @Test
    void getStats_ignoresOtherMonitors() {
        Monitor google = monitorRepository.save(monitor("Google"));
        Monitor github = monitorRepository.save(monitor("GitHub"));
        save(google, true, 100L, NOW.minusSeconds(60));
        save(github, false, null, NOW.minusSeconds(60));

        CheckStats stats = checkResultRepository.getStats(google.getId(), NOW.minus(Duration.ofHours(1)));

        assertThat(stats.getTotal()).isEqualTo(1);
        assertThat(stats.getFailed()).isZero();
    }

    @Test
    void getStats_returnsZeroTotalAndNulls_whenNoChecks() {
        Monitor monitor = monitorRepository.save(monitor("Google"));

        CheckStats stats = checkResultRepository.getStats(monitor.getId(), NOW.minus(Duration.ofHours(1)));

        assertThat(stats.getTotal()).isZero();
        // SUM and AVG over zero rows are NULL in SQL; the service turns them into 0 / null
        assertThat(stats.getFailed()).isNull();
        assertThat(stats.getAvgResponseMs()).isNull();
    }

    @Test
    void getStats_returnsNullAverage_whenAllChecksFailed() {
        Monitor monitor = monitorRepository.save(monitor("Google"));
        save(monitor, false, null, NOW.minusSeconds(60));
        save(monitor, false, 50L, NOW.minusSeconds(120));

        CheckStats stats = checkResultRepository.getStats(monitor.getId(), NOW.minus(Duration.ofHours(1)));

        assertThat(stats.getTotal()).isEqualTo(2);
        assertThat(stats.getFailed()).isEqualTo(2);
        assertThat(stats.getAvgResponseMs()).isNull();
    }

    // --- findByMonitorIdAndCheckedAtBetween ---

    @Test
    void findBetween_returnsOnlyChecksInRange() {
        Monitor monitor = monitorRepository.save(monitor("Google"));
        save(monitor, true, 100L, NOW.minus(Duration.ofHours(3)));
        save(monitor, true, 100L, NOW.minus(Duration.ofHours(2)));
        save(monitor, true, 100L, NOW.minus(Duration.ofHours(1)));

        Page<CheckResult> page = checkResultRepository.findByMonitorIdAndCheckedAtBetween(
                monitor.getId(), NOW.minus(Duration.ofMinutes(150)), NOW, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(CheckResult::getCheckedAt)
                .containsExactlyInAnyOrder(NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void findBetween_ignoresOtherMonitors() {
        Monitor google = monitorRepository.save(monitor("Google"));
        Monitor github = monitorRepository.save(monitor("GitHub"));
        save(google, true, 100L, NOW.minusSeconds(60));
        save(github, true, 100L, NOW.minusSeconds(60));

        Page<CheckResult> page = checkResultRepository.findByMonitorIdAndCheckedAtBetween(
                google.getId(), NOW.minus(Duration.ofHours(1)), NOW, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getMonitor().getId()).isEqualTo(google.getId());
    }

    @Test
    void findBetween_sortsNewestFirstAndPages() {
        Monitor monitor = monitorRepository.save(monitor("Google"));
        for (int i = 1; i <= 5; i++) {
            save(monitor, true, 100L, NOW.minus(Duration.ofMinutes(i)));
        }

        Page<CheckResult> firstPage = checkResultRepository.findByMonitorIdAndCheckedAtBetween(
                monitor.getId(), NOW.minus(Duration.ofHours(1)), NOW,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "checkedAt")));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(CheckResult::getCheckedAt)
                .containsExactly(NOW.minus(Duration.ofMinutes(1)), NOW.minus(Duration.ofMinutes(2)));
    }

    private void save(Monitor monitor, boolean up, Long responseTimeMs, Instant checkedAt) {
        CheckResult result = new CheckResult();
        result.setMonitor(monitor);
        result.setCheckedAt(checkedAt);
        result.setUp(up);
        result.setStatusCode(up ? 200 : null);
        result.setResponseTimeMs(responseTimeMs);
        result.setErrorMessage(up ? null : "Connection refused");
        checkResultRepository.save(result);
        entityManager.flush();
    }

    private static Monitor monitor(String name) {
        Monitor monitor = new Monitor();
        monitor.setName(name);
        monitor.setUrl("https://google.com");
        monitor.setIntervalSeconds(60);
        return monitor;
    }
}