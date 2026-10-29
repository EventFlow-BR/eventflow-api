package br.com.eventflow.shared.config;

import br.com.eventflow.event.dto.EventResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            ObjectMapper objectMapper,
            @Value("${spring.cache.redis.time-to-live:10m}")
            Duration cacheTtl
    ) {
        JacksonJsonRedisSerializer<EventResponse> serializer =
                new JacksonJsonRedisSerializer<>(
                        objectMapper,
                        EventResponse.class
                );

        RedisCacheConfiguration eventsCacheConfiguration =
                RedisCacheConfiguration
                        .defaultCacheConfig()
                        .entryTtl(cacheTtl)
                        .serializeValuesWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(serializer)
                        );

        return builder ->
                builder.withCacheConfiguration(
                        "events",
                        eventsCacheConfiguration
                );
    }
}