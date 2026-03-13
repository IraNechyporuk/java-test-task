package org.example.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RoundRobinLoadBalancer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.InetSocketAddress;
import java.net.Socket;

@Configuration
@Log4j2
@RequiredArgsConstructor
@EnableScheduling
public class RedissonConfig {

    // SecretClient is used in CLUSTER/REPLICATED/MASTER_SLAVE modes
    // to retrieve the Redis password from Azure Key Vault.
    // Not needed for local STANDALONE mode.
    // private final SecretClient secretClient;
    // @Value("${azure.keyVault.keys.redis-password}")
    // private String passwordKeyName;

    private final RedisClientHolder redisClientHolder;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${redis.masterAddress:redis://localhost:6379}")
    private String masterAddress;
    @Value("${redis.nodeAddresses:}")
    private String nodeAddresses;
    @Value("${redis.mode:STANDALONE}")
    private String mode;
    @Value("${redis.idleConnectionTimeout:30000}")
    private int idleConnectionTimeout;
    @Value("${redis.connectTimeout:15000}")
    private int connectTimeout;
    @Value("${redis.timeout:5000}")
    private int timeout;
    @Value("${redis.retryAttempts:3}")
    private int retryAttempts;
    @Value("${redis.retryInterval:3000}")
    private int retryInterval;
    @Value("${redis.failedSlaveReconnectionInterval:15000}")
    private int failedSlaveReconnectionInterval;
    @Value("${redis.failedSlaveCheckInterval:60000}")
    private int failedSlaveCheckInterval;
    @Value("${redis.subscriptionsPerConnection:5}")
    private int subscriptionsPerConnection;
    @Value("${redis.clientName:redisson}")
    private String clientName;
    @Value("${redis.subscriptionConnectionMinimumIdleSize:1}")
    private int subscriptionConnectionMinimumIdleSize;
    @Value("${redis.subscriptionConnectionPoolSize:50}")
    private int subscriptionConnectionPoolSize;
    @Value("${redis.slaveConnectionMinimumIdleSize:2}")
    private int slaveConnectionMinimumIdleSize;
    @Value("${redis.slaveConnectionPoolSize:24}")
    private int slaveConnectionPoolSize;
    @Value("${redis.masterConnectionMinimumIdleSize:2}")
    private int masterConnectionMinimumIdleSize;
    @Value("${redis.masterConnectionPoolSize:24}")
    private int masterConnectionPoolSize;
    @Value("${redis.scanInterval:1000}")
    private int scanInterval;
    @Value("${redis.pingConnectionInterval:60000}")
    private int pingConnectionInterval;
    @Value("${redis.keepAlive:false}")
    private boolean keepAlive;
    @Value("${redis.tcpNoDelay:true}")
    private boolean tcpNoDelay;

    @PostConstruct
    public void init() {
        log.info("Initializing Redis connection...");
        if (isRedisReachable()) {
            tryConnectToRedis();
        } else {
            log.warn("Redis is unavailable on startup — application running without Redis. Monitoring started (every 10s).");
        }
    }

    @Scheduled(fixedDelay = 10_000)
    public void checkRedisAvailability() {
        boolean reachable = isRedisReachable();
        boolean connected = redisClientHolder.isAvailable();

        if (connected && !reachable) {
            log.warn("Redis connection LOST. Switching to no-Redis mode.");
            redisClientHolder.clear();
            eventPublisher.publishEvent(new RedisLostEvent(this));
            return;
        }

        if (!connected && reachable) {
            log.info("Redis is back — reconnecting...");
            tryConnectToRedis();
            if (redisClientHolder.isAvailable()) {
                log.info("Redis reconnected. Notifying services.");
                eventPublisher.publishEvent(new RedisRestoredEvent(this));
            }
            return;
        }

        if (connected) {
            log.debug("Redis is stable. [{}]", masterAddress);
        } else {
            log.debug("Redis still unavailable. Waiting... [{}]", masterAddress);
        }
    }

    @PreDestroy
    public void destroy() {
        redisClientHolder.clear();
        log.info("Redis client stopped on context shutdown.");
    }

    private void tryConnectToRedis() {
        try {
            redisClientHolder.set(buildRedissonClient());
            log.info("Redis connected. Mode: {}", mode);
        } catch (Exception e) {
            redisClientHolder.clear();
            log.warn("Failed to connect to Redis: {}", e.getMessage());
        }
    }

    private boolean isRedisReachable() {
        try {
            String address = masterAddress.replaceFirst("rediss?://", "");
            String[] parts = address.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private RedissonClient buildRedissonClient() {
        Config config = new Config();
        switch (mode) {
            case "CLUSTER" -> {
                // String password = secretClient.getSecret(passwordKeyName).getValue();
                config.useClusterServers()
                        .setIdleConnectionTimeout(idleConnectionTimeout)
                        .setConnectTimeout(connectTimeout)
                        .setTimeout(timeout)
                        .setRetryAttempts(retryAttempts)
                        .setRetryInterval(retryInterval)
                        .setFailedSlaveReconnectionInterval(failedSlaveReconnectionInterval)
                        // .setFailedSlaveNodeDetector(new FailedConnectionDetector(failedSlaveCheckInterval))
                        .setSubscriptionsPerConnection(subscriptionsPerConnection)
                        .setClientName(clientName)
                        .setLoadBalancer(new RoundRobinLoadBalancer())
                        .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                        .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                        .setSlaveConnectionMinimumIdleSize(slaveConnectionMinimumIdleSize)
                        .setSlaveConnectionPoolSize(slaveConnectionPoolSize)
                        .setMasterConnectionMinimumIdleSize(masterConnectionMinimumIdleSize)
                        .setMasterConnectionPoolSize(masterConnectionPoolSize)
                        .setReadMode(ReadMode.MASTER_SLAVE)
                        .setSubscriptionMode(SubscriptionMode.MASTER)
                        .setScanInterval(scanInterval)
                        .setPingConnectionInterval(pingConnectionInterval)
                        .setKeepAlive(keepAlive)
                        .setTcpNoDelay(tcpNoDelay)
                        .addNodeAddress(nodeAddresses.split(","));
                // .setPassword(password);
                return Redisson.create(config);
            }
            case "REPLICATED" -> {
                // String password = secretClient.getSecret(passwordKeyName).getValue();
                config.useReplicatedServers()
                        .setIdleConnectionTimeout(idleConnectionTimeout)
                        .setConnectTimeout(connectTimeout)
                        .setTimeout(timeout)
                        .setRetryAttempts(retryAttempts)
                        .setRetryInterval(retryInterval)
                        .setFailedSlaveReconnectionInterval(failedSlaveReconnectionInterval)
                        // .setFailedSlaveNodeDetector(new FailedConnectionDetector(failedSlaveCheckInterval))
                        .setSubscriptionsPerConnection(subscriptionsPerConnection)
                        .setClientName(clientName)
                        .setLoadBalancer(new RoundRobinLoadBalancer())
                        .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                        .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                        .setSlaveConnectionMinimumIdleSize(slaveConnectionMinimumIdleSize)
                        .setSlaveConnectionPoolSize(slaveConnectionPoolSize)
                        .setMasterConnectionMinimumIdleSize(masterConnectionMinimumIdleSize)
                        .setMasterConnectionPoolSize(masterConnectionPoolSize)
                        .setReadMode(ReadMode.MASTER_SLAVE)
                        .setSubscriptionMode(SubscriptionMode.MASTER)
                        .setScanInterval(scanInterval)
                        .setPingConnectionInterval(pingConnectionInterval)
                        .setKeepAlive(keepAlive)
                        .setTcpNoDelay(tcpNoDelay)
                        .addNodeAddress(nodeAddresses.split(","));
                // .setPassword(password);
                return Redisson.create(config);
            }
            case "MASTER_SLAVE" -> {
                // String password = secretClient.getSecret(passwordKeyName).getValue();
                config.useMasterSlaveServers()
                        .setIdleConnectionTimeout(idleConnectionTimeout)
                        .setConnectTimeout(connectTimeout)
                        .setTimeout(timeout)
                        .setRetryAttempts(retryAttempts)
                        .setRetryInterval(retryInterval)
                        .setFailedSlaveReconnectionInterval(failedSlaveReconnectionInterval)
                        // .setFailedSlaveNodeDetector(new FailedConnectionDetector(failedSlaveCheckInterval))
                        .setSubscriptionsPerConnection(subscriptionsPerConnection)
                        .setClientName(clientName)
                        .setLoadBalancer(new RoundRobinLoadBalancer())
                        .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                        .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                        .setSlaveConnectionMinimumIdleSize(slaveConnectionMinimumIdleSize)
                        .setSlaveConnectionPoolSize(slaveConnectionPoolSize)
                        .setMasterConnectionMinimumIdleSize(masterConnectionMinimumIdleSize)
                        .setMasterConnectionPoolSize(masterConnectionPoolSize)
                        .setReadMode(ReadMode.MASTER_SLAVE)
                        .setSubscriptionMode(SubscriptionMode.MASTER)
                        .setPingConnectionInterval(pingConnectionInterval)
                        .setKeepAlive(keepAlive)
                        .setTcpNoDelay(tcpNoDelay)
                        .setMasterAddress(masterAddress)
                        .addSlaveAddress(nodeAddresses.split(","));
                // .setPassword(password);
                return Redisson.create(config);
            }
            default -> {
                config.useSingleServer()
                        .setIdleConnectionTimeout(idleConnectionTimeout)
                        .setConnectTimeout(connectTimeout)
                        .setTimeout(timeout)
                        .setRetryAttempts(retryAttempts)
                        .setRetryInterval(retryInterval)
                        .setSubscriptionsPerConnection(subscriptionsPerConnection)
                        .setClientName(clientName)
                        .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                        .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                        .setPingConnectionInterval(pingConnectionInterval)
                        .setKeepAlive(keepAlive)
                        .setTcpNoDelay(tcpNoDelay)
                        .setAddress(masterAddress);
                return Redisson.create(config);
            }
        }
    }
}