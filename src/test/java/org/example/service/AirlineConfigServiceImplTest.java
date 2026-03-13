package org.example.service;

import org.example.config.RedisClientHolder;
import org.example.model.AirlineOrg;
import org.example.model.LogEntry;
import org.example.model.PayloadAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AirlineConfigServiceImplTest {

    @Mock private AppLogService appLogService;
    @Mock private RedisClientHolder redisClientHolder;
    @Mock private RedissonClient redissonClient;
    @Mock private RTopic rTopic;

    private LogEntry logEntry;

    @InjectMocks
    private AirlineConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        logEntry = new LogEntry("test-corr", "test-op");
        lenient().when(appLogService.logInternalRQ(any(), any(), any(), any(), any()))
                .thenReturn(logEntry);
    }

    // ─── Scenario 1: Redis unavailable on startup ────────────────────

    @Test
    @DisplayName("1a. Redis down on startup — does not throw and does not interact with RedissonClient")
    void whenRedisDownAtStart_initShouldNotThrowAndNotSubscribe() {
        when(redisClientHolder.isAvailable()).thenReturn(false);
        service.initRefreshListener();
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("1b. Redis down — returns value and caches it on second call")
    void whenRedisDown_shouldReturnAndCache() {
        when(redisClientHolder.isAvailable()).thenReturn(false);
        PayloadAttributes attrs = new PayloadAttributes("corr-1", "tenant-1");

        AirlineOrg result1 = service.getAirlineConfiguration("AA", attrs);
        assertThat(result1).isNotNull();
        assertThat(result1.getAirlineId()).isEqualTo("AA");

        AirlineOrg result2 = service.getAirlineConfiguration("AA", attrs);
        assertThat(result1).isSameAs(result2);

        verify(appLogService, times(1)).logInternalRQ(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("1c. Redis down — different airlines are cached independently")
    void whenRedisDown_differentAirlines_cachedSeparately() {
        when(redisClientHolder.isAvailable()).thenReturn(false);
        PayloadAttributes attrs = new PayloadAttributes("corr-1", "tenant-1");

        AirlineOrg aa = service.getAirlineConfiguration("AA", attrs);
        AirlineOrg bb = service.getAirlineConfiguration("BB", attrs);

        assertThat(aa.getAirlineId()).isEqualTo("AA");
        assertThat(bb.getAirlineId()).isEqualTo("BB");
        assertThat(aa).isNotSameAs(bb);

        verify(appLogService, times(2)).logInternalRQ(any(), any(), any(), any(), any());
    }

    // ─── Scenario 2: Redis restored ──────────────────────────────────

    @Test
    @DisplayName("2a. onRedisRestored — local cache is cleared, next call fetches fresh data")
    void whenRedisRestored_cacheClearedAndFetchedAgain() {
        when(redisClientHolder.isAvailable()).thenReturn(false);
        PayloadAttributes attrs = new PayloadAttributes("corr-1", "tenant-1");

        service.getAirlineConfiguration("AA", attrs);
        verify(appLogService, times(1)).logInternalRQ(any(), any(), any(), any(), any());

        when(redisClientHolder.getClient()).thenReturn(null);
        service.onRedisRestored();

        service.getAirlineConfiguration("AA", attrs);
        verify(appLogService, times(2)).logInternalRQ(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("2b. onRedisRestored — removeAllListeners called before each new subscription")
    void whenRedisRestored_removesOldListenersBeforeSubscribing() {
        when(redisClientHolder.getClient()).thenReturn(redissonClient);
        when(redissonClient.getTopic(anyString())).thenReturn(rTopic);

        service.onRedisRestored();
        service.onRedisRestored();

        verify(rTopic, times(2)).removeAllListeners();
        verify(rTopic, times(2)).addListener(eq(String.class), any());
    }

    @Test
    @DisplayName("2c. Redis available on startup — subscribes to RTopic")
    void whenRedisAvailableAtStart_subscribesToTopic() {
        when(redisClientHolder.isAvailable()).thenReturn(true);
        when(redisClientHolder.getClient()).thenReturn(redissonClient);
        when(redissonClient.getTopic(anyString())).thenReturn(rTopic);

        service.initRefreshListener();

        verify(rTopic).removeAllListeners();
        verify(rTopic).addListener(eq(String.class), any());
    }

    // ─── Scenario 3: Cache isolation by tenant ───────────────────────

    @Test
    @DisplayName("3. Same airline with different tenants — cached as separate entries")
    void sameAirline_differentTenants_cachedSeparately() {
        when(redisClientHolder.isAvailable()).thenReturn(false);

        AirlineOrg result1 = service.getAirlineConfiguration("AA",
                new PayloadAttributes("corr-1", "tenant-1"));
        AirlineOrg result2 = service.getAirlineConfiguration("AA",
                new PayloadAttributes("corr-2", "tenant-2"));

        assertThat(result1).isNotSameAs(result2);
        assertThat(result1.getTenantId()).isEqualTo("tenant-1");
        assertThat(result2.getTenantId()).isEqualTo("tenant-2");

        verify(appLogService, times(2)).logInternalRQ(any(), any(), any(), any(), any());
    }

    // ─── Scenario 4: Redis lost ───────────────────────────────────────

    @Test
    @DisplayName("4. onRedisLost — local cache is preserved, requests continue to be served")
    void whenRedisLost_cachePreservedAndRequestsServed() {
        when(redisClientHolder.isAvailable()).thenReturn(false);
        PayloadAttributes attrs = new PayloadAttributes("corr-1", "tenant-1");

        AirlineOrg before = service.getAirlineConfiguration("AA", attrs);
        service.onRedisLost();
        AirlineOrg after = service.getAirlineConfiguration("AA", attrs);

        assertThat(before).isSameAs(after);
        verify(appLogService, times(1)).logInternalRQ(any(), any(), any(), any(), any());
    }
}