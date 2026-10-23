package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.dto.MonitorRequest;
import com.project.uptimely.uptimely1.dto.MonitorResponse;
import com.project.uptimely.uptimely1.dto.UpdateMonitorRequest;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.exception.MonitorNotFoundException;
import com.project.uptimely.uptimely1.repository.MonitorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;

    public MonitorService(MonitorRepository monitorRepository) {
        this.monitorRepository = monitorRepository;
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

    private Monitor findOrThrow(Long id) {
        return monitorRepository.findById(id)
                .orElseThrow(() -> new MonitorNotFoundException(id));
    }
}
