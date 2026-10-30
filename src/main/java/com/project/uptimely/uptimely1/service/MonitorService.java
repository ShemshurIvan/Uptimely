package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.dto.*;
import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import com.project.uptimely.uptimely1.exception.InvalidRequestException;
import com.project.uptimely.uptimely1.exception.MonitorNotFoundException;
import com.project.uptimely.uptimely1.repository.CheckResultRepository;
import com.project.uptimely.uptimely1.repository.CheckStats;
import com.project.uptimely.uptimely1.repository.MonitorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.project.uptimely.uptimely1.entity.MonitorStatus.DOWN;
import static com.project.uptimely.uptimely1.entity.MonitorStatus.UP;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final HealthCheckService healthCheckService;
    private final CheckResultRepository checkResultRepository;
    private final AlertService alertService;

    public MonitorService(MonitorRepository monitorRepository, HealthCheckService healthCheckService,
                          CheckResultRepository checkResultRepository, AlertService alertService) {
        this.monitorRepository = monitorRepository;
        this.healthCheckService = healthCheckService;
        this.checkResultRepository = checkResultRepository;
        this.alertService = alertService;
    }

    @Transactional
    public MonitorResponse create(MonitorRequest monitorRequest) {
        Monitor monitor = new Monitor();
        monitor.setName(monitorRequest.name());
        monitor.setUrl(monitorRequest.url());
        monitor.setIntervalSeconds(monitorRequest.intervalSeconds());

        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional(readOnly = true)
    public Page<MonitorResponse> getAll(Pageable pageable) {
        return monitorRepository.findAll(pageable).map(MonitorResponse::from);
    }

    @Transactional(readOnly = true)
    public MonitorResponse getById(Long id) {
        Monitor monitor = findOrThrow(id);

        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse update(Long id, UpdateMonitorRequest updateMonitorRequest) {
        Monitor monitor = findOrThrow(id);
        monitor.setName(updateMonitorRequest.name());
        monitor.setUrl(updateMonitorRequest.url());
        monitor.setIntervalSeconds(updateMonitorRequest.intervalSeconds());

        return MonitorResponse.from(monitor);
    }

    @Transactional
    public void deleteById(Long id) {
        Monitor monitor = findOrThrow(id);
        monitorRepository.delete(monitor);
    }

    @Transactional
    public MonitorResponse pause(Long id) {
        Monitor monitor = findOrThrow(id);

        monitor.setActive(false);
        return MonitorResponse.from(monitor);
    }

    @Transactional
    public MonitorResponse resume(Long id) {
        Monitor monitor = findOrThrow(id);

        monitor.setActive(true);
        return MonitorResponse.from(monitor);
    }

    public CheckResult recordResult(CheckResult result) {
        Monitor monitor = result.getMonitor();
        MonitorStatus previous = monitor.getCurrentStatus();

        CheckResult saved = checkResultRepository.save(result);
        monitorRepository.updateStatus(
                monitor.getId(),
                saved.getUp() ? UP : DOWN,
                saved.getCheckedAt());

        alertService.notifyIfChanged(monitor, previous, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<CheckResultResponse> getChecks(Long id, Instant from, Instant to, Pageable pageable) {
        findOrThrow(id);

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(StatsPeriod.LAST_24H.getDuration());
        if (start.isAfter(end)) {
            throw new InvalidRequestException("'from' must be before 'to'");
        }

        return checkResultRepository.findByMonitorIdAndCheckedAtBetween(id, start, end, pageable)
                .map(CheckResultResponse::from);
    }

    @Transactional(readOnly = true)
    public MonitorStatsResponse getStats(Long id, String period) {
        findOrThrow(id);

        StatsPeriod statsPeriod = StatsPeriod.fromValue(period);
        Instant to = Instant.now();
        Instant from = to.minus(statsPeriod.getDuration());

        CheckStats stats = checkResultRepository.getStats(id, from);
        long total = stats.getTotal();
        long failed = stats.getFailed() != null ? stats.getFailed() : 0;
        Double uptimePercent = total == 0 ? null : round((total - failed) * 100.0 / total);
        Double avgResponseMs = stats.getAvgResponseMs() != null ? round(stats.getAvgResponseMs()) : null;

        return new MonitorStatsResponse(id, statsPeriod.getValue(), from, to,
                total, failed, uptimePercent, avgResponseMs);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    public CheckResultResponse checkNow(Long id) {
        Monitor monitor = findOrThrow(id);

        CheckResult result = healthCheckService.check(monitor).block();

        return CheckResultResponse.from(recordResult(result));
    }

    private Monitor findOrThrow(Long id) {
        return monitorRepository.findById(id)
                .orElseThrow(() -> new MonitorNotFoundException(id));
    }
}
