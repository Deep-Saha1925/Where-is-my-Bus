package com.deep.WIMB.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // RedisLocationService already hand-serializes Location objects to JSON
    // strings itself (via its own ObjectMapper) before pushing them into
    // Redis lists, and reads them back the same way. So this template only
    // ever needs to move plain strings in and out — String keys, String
    // values. Previously this bean was declared as RedisTemplate<String,
    // Object> with a polymorphic JSON value serializer, which (a) didn't
    // match the RedisTemplate<String, String> that RedisLocationService
    // actually injects — generics are invariant, so that's a genuine bean
    // type mismatch, not just a cosmetic one — and (b) caused every value
    // to be JSON-serialized twice (once by hand, once by the template).
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}