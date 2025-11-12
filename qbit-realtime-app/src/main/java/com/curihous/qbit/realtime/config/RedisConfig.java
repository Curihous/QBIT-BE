package com.curihous.qbit.realtime.config;

import com.curihous.qbit.infra.redis.config.RedisSerializerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정
 * 
 * redisTemplate: TradeUpdateProducer에서 Redis Streams에 메시지 발행 시 사용
 * qbit-infra의 RedisConfig와 동일한 직렬화 방식 사용
 */
@Configuration
@Import(RedisSerializerConfig.class)
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        
        // Value serializer
        template.setValueSerializer(genericJackson2JsonRedisSerializer);
        
        // Hash serializer
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(genericJackson2JsonRedisSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

}

