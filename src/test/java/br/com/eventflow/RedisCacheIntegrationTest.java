package br.com.eventflow;

import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RedisCacheIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void shouldUseRedisCacheManager() {
        assertInstanceOf(
                RedisCacheManager.class,
                cacheManager
        );
    }
}