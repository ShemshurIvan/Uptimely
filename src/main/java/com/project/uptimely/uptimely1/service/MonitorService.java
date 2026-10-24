package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.dto.CheckResultResponse;
import com.project.uptimely.uptimely1.dto.MonitorRequest;
import com.project.uptimely.uptimely1.dto.MonitorResponse;
import com.project.uptimely.uptimely1.dto.UpdateMonitorRequest;
import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.exception.MonitorNotFoundException;
import com.project.uptimely.uptimely1.repository.CheckResultRepository;
import com.project.uptimely.uptimely1.repository.MonitorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import static com.project.uptimely.uptimely1.entity.MonitorStatus.DOWN;
import static com.project.uptimely.uptimely1.entity.MonitorStatus.UP;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final HealthCheckService healthCheckService;
    private final CheckResultRepository checkResultRepository;

    public MonitorService(MonitorRepository monitorRepository, HealthCheckService healthCheckService, CheckResultRepository checkResultRepository) {
        this.monitorRepository = monitorRepository;
        this.healthCheckService = healthCheckService;
        this.checkResultRepository = checkResultRepository;
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
        CheckResult saved = checkResultRepository.save(result);
        monitorRepository.updateStatus(
                result.getMonitor().getId(),
                saved.getUp() ? UP : DOWN,
                saved.getCheckedAt());
        return saved;
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
