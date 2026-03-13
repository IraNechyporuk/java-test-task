package org.example.model;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class LogEntry {

    private final String correlationId;
    private final String operation;

    public LogEntry(String correlationId, String operation) {
        this.correlationId = correlationId;
        this.operation = operation;
    }

    public void logRS(Object response, Outcome outcome, String source) {
        log.trace("[{}] {} -> {} from {}", correlationId, operation, outcome, source);
    }
}