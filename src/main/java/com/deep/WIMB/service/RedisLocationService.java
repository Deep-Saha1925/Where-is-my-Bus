package com.deep.WIMB.service;

import com.deep.WIMB.model.Location;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLocationService {

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final String REDIS_KEY = "location:ride:";

    public void saveLocationToRedis(Location location){
        try{
            String key = REDIS_KEY + location.getRide().getId();
            String json = objectMapper.writeValueAsString(location);

            redisTemplate.opsForList().rightPush(key, json);
            log.info("Saved location to Redis | key={}", key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize location: {}", e.getMessage());
        }
    }

    public List<Location> getLocationsFromRedis(Long rideId){
        String key = REDIS_KEY + rideId;
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        List<Location> locations = new ArrayList<>();

        if(jsonList == null) return locations;

        for(String json : jsonList){
            try{
                locations.add(objectMapper.readValue(json, Location.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize location: {}", e.getMessage());
            }
        }

        return locations;
    }

    public void clearLocationsFromRedis(Long rideId){
        String key = REDIS_KEY + rideId;
        redisTemplate.delete(key);
        log.info("Cleared Redis key={}", key);
    }

    public java.util.Set<String> getAllLocationKeys() {
        return redisTemplate.keys(REDIS_KEY + "*");
    }

}
