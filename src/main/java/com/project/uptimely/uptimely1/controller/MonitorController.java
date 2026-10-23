package com.project.uptimely.uptimely1.controller;

import com.project.uptimely.uptimely1.dto.MonitorRequest;
import com.project.uptimely.uptimely1.dto.MonitorResponse;
import com.project.uptimely.uptimely1.dto.UpdateMonitorRequest;
import com.project.uptimely.uptimely1.service.MonitorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorResponse createMonitor(@RequestBody @Valid MonitorRequest monitorRequest) {
        return monitorService.create(monitorRequest);
    }

    @GetMapping()
    public PagedModel<MonitorResponse> getAllMonitors(@PageableDefault Pageable pageable) {
        return new PagedModel<>(monitorService.getAll(pageable));
    }

    @GetMapping("/{id}")
    public MonitorResponse getMonitorById(@PathVariable Long id) {
        return monitorService.getById(id);
    }

    @PutMapping("/{id}")
    public MonitorResponse updateMonitor(@PathVariable Long id,
                                         @RequestBody @Valid UpdateMonitorRequest monitorRequest) {
        return monitorService.update(id, monitorRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMonitor(@PathVariable Long id) {
        monitorService.deleteById(id);
    }

    @PatchMapping("/{id}/pause")
    public MonitorResponse pauseMonitor(@PathVariable Long id) {
        return monitorService.pause(id);
    }

    @PatchMapping("/{id}/resume")
    public MonitorResponse resumeMonitor(@PathVariable Long id) {
        return monitorService.resume(id);
    }
}
