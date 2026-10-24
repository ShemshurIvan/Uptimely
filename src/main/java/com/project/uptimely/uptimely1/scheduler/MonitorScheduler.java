package com.project.uptimely.uptimely1.scheduler;

import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.repository.MonitorRepository;
import com.project.uptimely.uptimely1.service.HealthCheckService;
import com.project.uptimely.uptimely1.service.MonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class MonitorScheduler {

    private final MonitorRepository monitorRepository;
    private final HealthCheckService healthCheckService;
    private final MonitorService monitorService;
    private final int concurrency;

    public MonitorScheduler(MonitorRepository monitorRepository,
                            HealthCheckService healthCheckService,
                            MonitorService monitorService,
                            @Value("${uptimely.scheduler.concurrency}") int concurrency) {

        this.monitorRepository = monitorRepository;
        this.healthCheckService = healthCheckService;
        this.monitorService = monitorService;
        this.concurrency = concurrency;
    }

    @Scheduled(fixedDelayString = "${uptimely.scheduler.tick}")
    public void runDueChecks() {
        Instant now = Instant.now();

        List<Monitor> monitors = monitorRepository.findByActiveTrue().stream()
                .filter(m -> m.isDue(now))
                .toList();
        if(monitors.isEmpty()) {
            return;
        }
        log.info("Checking {} monitor(s)", monitors.size());
        List<CheckResult> results = Flux.fromIterable(monitors)
                .flatMap(healthCheckService::check, concurrency)
                .collectList()
                .block();

        for (CheckResult result : results) {
            try {
                monitorService.recordResult(result);
            } catch (Exception e) {
                log.warn("Could not save result for monitor {}", result.getMonitor().getId(), e);
            }
        }
    }
}

