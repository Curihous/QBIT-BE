package com.curihous.qbit.infra.redis.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 캐시 설정 - BinanceMarketServce, MassiveMarketService에서 사용
 * 
 * 현재 캐시 전략:
 * - binance-ticker: 1초 (Binance 24시간 통계)
 * - binance-kline: 5분 (Binance 캔들 데이터)
 * - massive-ticker: 1분 (전일 종가 데이터)
 * - massive-aggregate: 5분 (집계/차트 데이터)
 * - massive-snapshot: 1초 (Snapshot 데이터 - 시세 조회용)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer
    ) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(genericJackson2JsonRedisSerializer)
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("binance-ticker", defaultConfig.entryTtl(Duration.ofSeconds(1)))
                .withCacheConfiguration("binance-kline", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("massive-ticker", defaultConfig.entryTtl(Duration.ofMinutes(1)))
                .withCacheConfiguration("massive-aggregate", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("massive-snapshot", defaultConfig.entryTtl(Duration.ofSeconds(1)))
                .build();
    }
}

