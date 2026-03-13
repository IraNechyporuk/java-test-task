package org.example.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Log4j2
public class ApplicationPropertiesLogger {

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "secret", "key", "token", "credential", "auth", "private"
    );

    private final Environment environment;
    private final AtomicBoolean logged = new AtomicBoolean(false);

    public ApplicationPropertiesLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void logAllProperties() {
        if (!logged.compareAndSet(false, true)) return;

        MutablePropertySources propertySources = ((AbstractEnvironment) environment).getPropertySources();
        TreeMap<String, String> allProperties = new TreeMap<>();

        for (org.springframework.core.env.PropertySource<?> source : propertySources) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    String value = environment.getProperty(name);
                    allProperties.put(name, isSensitive(name) ? "*****" : (value != null ? value : "<null>"));
                }
            }
        }

        log.info("=== APPLICATION PROPERTIES ON STARTUP ({} total) ===", allProperties.size());
        allProperties.forEach((k, v) -> log.info("  {}={}", k, v));
        log.info("=== END OF PROPERTIES ===");
    }

    private boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }
}