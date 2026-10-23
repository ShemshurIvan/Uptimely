package com.project.uptimely.uptimely1.exception;

public class MonitorNotFoundException extends RuntimeException {
    public MonitorNotFoundException(Long id) {
        super("Monitor with id " + id + " not found");
    }
}
