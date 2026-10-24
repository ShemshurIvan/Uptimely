package com.project.uptimely.uptimely1.scheduler;

import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.repository.MonitorRepository;
import com.project.uptimely.uptimely1.service.HealthCheckService;
import com.project.uptimely.uptimely1.service.MonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorSchedulerTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private HealthCheckService healthCheckService;

    @Mock
    private MonitorService monitorService;

    private MonitorScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MonitorScheduler(monitorRepository, healthCheckService, monitorService, 10);
    }

    @Test
    void checksOnlyDueMonitors() {
        Monitor neverChecked = monitor(1L, 60, null);
        Monitor overdue = monitor(2L, 60, Instant.now().minusSeconds(120));
        Monitor notYet = monitor(3L, 60, Instant.now().minusSeconds(10));
        when(monitorRepository.findByActiveTrue()).thenReturn(List.of(neverChecked, overdue, notYet));
        when(healthCheckService.check(any())).thenAnswer(inv -> Mono.just(result(inv.getArgument(0))));

        scheduler.runDueChecks();

        verify(healthCheckService).check(neverChecked);
        verify(healthCheckService).check(overdue);
        verify(healthCheckService, never()).check(notYet);
    }

    @Test
    void recordsEveryResult() {
        Monitor a = monitor(1L, 60, null);
        Monitor b = monitor(2L, 60, null);
        CheckResult resultA = result(a);
        CheckResult resultB = result(b);
        when(monitorRepository.findByActiveTrue()).thenReturn(List.of(a, b));
        when(healthCheckService.check(a)).thenReturn(Mono.just(resultA));
        when(healthCheckService.check(b)).thenReturn(Mono.just(resultB));

        scheduler.runDueChecks();

        verify(monitorService).recordResult(resultA);
        verify(monitorService).recordResult(resultB);
    }

    @Test
    void doesNothing_whenNoMonitorIsDue() {
        when(monitorRepository.findByActiveTrue()).thenReturn(List.of(monitor(1L, 60, Instant.now())));

        scheduler.runDueChecks();

        verify(healthCheckService, never()).check(any());
        verify(monitorService, never()).recordResult(any());
    }

    @Test
    void oneFailedSave_doesNotStopTheOthers() {
        Monitor a = monitor(1L, 60, null);
        Monitor b = monitor(2L, 60, null);
        CheckResult resultA = result(a);
        CheckResult resultB = result(b);
        when(monitorRepository.findByActiveTrue()).thenReturn(List.of(a, b));
        when(healthCheckService.check(a)).thenReturn(Mono.just(resultA));
        when(healthCheckService.check(b)).thenReturn(Mono.just(resultB));
        when(monitorService.recordResult(resultA)).thenThrow(new RuntimeException("monitor was deleted"));

        scheduler.runDueChecks();

        verify(monitorService).recordResult(resultB);
    }

    @Test
    void runsChecksInParallel() {
        List<Monitor> monitors = List.of(
                monitor(1L, 60, null), monitor(2L, 60, null), monitor(3L, 60, null),
                monitor(4L, 60, null), monitor(5L, 60, null));
        when(monitorRepository.findByActiveTrue()).thenReturn(monitors);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        when(healthCheckService.check(any())).thenAnswer(inv -> Mono.defer(() -> {
                    maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    return Mono.just(result(inv.getArgument(0)));
                })
                // simulate a 200ms HTTP call
                .delayElement(Duration.ofMillis(200))
                .doOnNext(r -> inFlight.decrementAndGet()));

        long start = System.nanoTime();
        scheduler.runDueChecks();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // sequentially this would take 5 x 200ms = 1000ms
        assertThat(maxInFlight.get()).isEqualTo(5);
        assertThat(elapsedMs).isLessThan(800);
    }

    private static Monitor monitor(Long id, int intervalSeconds, Instant lastCheckedAt) {
        Monitor monitor = new Monitor();
        ReflectionTestUtils.setField(monitor, "id", id);
        monitor.setName("m" + id);
        monitor.setUrl("https://example.com/" + id);
        monitor.setIntervalSeconds(intervalSeconds);
        monitor.setLastCheckedAt(lastCheckedAt);
        return monitor;
    }

    private static CheckResult result(Monitor monitor) {
        CheckResult result = new CheckResult();
        result.setMonitor(monitor);
        result.setCheckedAt(Instant.now());
        result.setUp(true);
        result.setStatusCode(200);
        return result;
    }
}