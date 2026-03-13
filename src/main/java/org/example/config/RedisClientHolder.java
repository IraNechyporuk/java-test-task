package org.example.config;

import lombok.extern.log4j.Log4j2;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
@Log4j2
public class RedisClientHolder {

    private final AtomicReference<RedissonClient> clientRef = new AtomicReference<>(null);

    public RedissonClient getClient() {
        return clientRef.get();
    }

    public boolean isAvailable() {
        return clientRef.get() != null;
    }

    public void set(RedissonClient newClient) {
        RedissonClient old = clientRef.getAndSet(newClient);
        if (old != null && !old.isShutdown()) {
            old.shutdown();
            log.info("Previous Redis client shut down.");
        }
    }

    public void clear() {
        RedissonClient old = clientRef.getAndSet(null);
        if (old != null && !old.isShutdown()) {
            old.shutdown();
        }
    }
}