package org.example.config;

import org.springframework.context.ApplicationEvent;

public class RedisRestoredEvent extends ApplicationEvent {
    public RedisRestoredEvent(Object source) {
        super(source);
    }
}