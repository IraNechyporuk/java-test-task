package org.example.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.example.config.RedisClientHolder;
import org.example.config.RedisLostEvent;
import org.example.config.RedisRestoredEvent;
import org.example.model.AirlineGroupOrg;
import org.example.model.AirlineOrg;
import org.example.model.LogEntry;
import org.example.model.Outcome;
import org.example.model.PayloadAttributes;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

@Service
@Log4j2
@RequiredArgsConstructor
public class AirlineConfigServiceImpl implements AirlineConfigService {

    private final AppLogService appLogService;
    private final RedisClientHolder redisClientHolder;

    private final ConcurrentHashMap<String, AirlineOrg> airlineOrgMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AirlineGroupOrg> airlineGroupOrgMap = new ConcurrentHashMap<>();

    @Value("${iagl.tenantId:IAGL}")
    private String iaglTenantId;

    @PostConstruct
    void initRefreshListener() {
        if (redisClientHolder.isAvailable()) {
            subscribeToRefreshTopic();
        } else {
            log.warn("Redis unavailable on startup — RTopic listener will be initialized after Redis is restored.");
        }
    }

    @EventListener(RedisRestoredEvent.class)
    public void onRedisRestored() {
        log.info("Redis restored — clearing local cache and resubscribing...");
        airlineOrgMap.clear();
        airlineGroupOrgMap.clear();
        subscribeToRefreshTopic();
    }

    @EventListener(RedisLostEvent.class)
    public void onRedisLost() {
        // Local cache is kept intentionally — requests continue to be served from it.
        // Cache will be cleared when Redis is restored in onRedisRestored().
        log.warn("Redis unavailable — falling back to local cache. Data may be stale.");
    }

    private void subscribeToRefreshTopic() {
        try {
            RedissonClient client = redisClientHolder.getClient();
            if (client == null) return;

            RTopic topic = client.getTopic("permissionManager:airlineConfig:refresh:tenantAirlineId");
            topic.removeAllListeners();
            topic.addListener(String.class, (channel, tenantAirlineId) -> {
                log.info("Cache invalidation received for: {}", tenantAirlineId);
                airlineOrgMap.remove(tenantAirlineId);
            });
            log.info("RTopic listener initialized.");
        } catch (Exception e) {
            log.warn("Failed to subscribe to RTopic: {}", e.getMessage());
        }
    }

    @Override
    public AirlineOrg getAirlineConfiguration(String airlineId, PayloadAttributes payloadAttributes) {
        String correlationId = payloadAttributes.getCorrelationId();
        String tenantId = payloadAttributes.getTenantName();
        final String finalTenantId = StringUtils.hasText(tenantId) ? tenantId : iaglTenantId;
        final String cacheKey = finalTenantId + airlineId;

        if (!redisClientHolder.isAvailable()) {
            log.debug("Redis unavailable — serving from local cache. Key: {}", cacheKey);
        }

        AirlineOrg cached = airlineOrgMap.get(cacheKey);
        if (cached != null) return cached;
        return airlineOrgMap.computeIfAbsent(cacheKey, k ->
                fetchAirlineOrg(airlineId, finalTenantId, correlationId)
        );
    }

    private AirlineOrg fetchAirlineOrg(String airlineId, String tenantId, String correlationId) {
        LogEntry logEntry = appLogService.logInternalRQ(correlationId, "getAirlineConfigs", airlineId, Level.TRACE, null);

        // Simplified stub replacing actual Thrift call to PermissionsManagementService
        AirlineOrg org = new AirlineOrg();
        org.setAirlineId(airlineId);
        org.setTenantId(tenantId);
        org.setName("Airline-" + airlineId);

        logEntry.logRS(org, Outcome.SUCCESS, "hardcoded.getAirlineOrg");
        return org;
    }
}