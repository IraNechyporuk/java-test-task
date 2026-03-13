package org.example.service;

import org.apache.logging.log4j.Level;
import org.example.model.LogEntry;
import org.springframework.stereotype.Service;

@Service
public class AppLogService {

    public LogEntry logInternalRQ(String correlationId, String operation, String payload, Level level, Object extra) {
        return new LogEntry(correlationId, operation);
    }
}