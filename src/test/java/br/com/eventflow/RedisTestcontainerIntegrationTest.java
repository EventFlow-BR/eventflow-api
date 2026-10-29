package br.com.eventflow;

import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisTestcontainerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldStoreAndReadValueFromRedisContainer() {
        String key =
                "test:" + UUID.randomUUID();

        String value =
                "eventflow";

        redisTemplate
                .opsForValue()
                .set(key, value);

        String storedValue =
                redisTemplate
                        .opsForValue()
                        .get(key);

        assertEquals(
                value,
                storedValue
        );

        redisTemplate.delete(key);
    }
}