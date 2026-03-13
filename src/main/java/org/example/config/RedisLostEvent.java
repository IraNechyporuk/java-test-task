package org.example.config;

import org.springframework.context.ApplicationEvent;

public class RedisLostEvent extends ApplicationEvent {
    public RedisLostEvent(Object source) {
        super(source);
    }
}