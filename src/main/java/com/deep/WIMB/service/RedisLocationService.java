package com.deep.WIMB.service;

import com.deep.WIMB.model.Location;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLocationService {

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final String REDIS_KEY = "location:ride:";

    /**
     * Pushes a location onto this ride's Redis list.
     *
     * @return true if it was actually saved to Redis, false if Redis
     *         couldn't be reached or the write otherwise failed. Previously
     *         this only caught JsonProcessingException, so a genuine Redis
     *         connection failure (RedisConnectionFailureException etc.) was
     *         never caught here at all — it propagated straight out of
     *         addLocation()/startRide() as an unhandled 500, AND (this is
     *         the part that actually broke live tracking) there was nothing
     *         downstream to notice the write never happened. A driver's
     *         location updates would keep "succeeding" from the app's point
     *         of view while Redis was down, with literally nothing being
     *         stored anywhere — so every live bus vanished from every
     *         passenger search until Redis came back, with no error visible
     *         to anyone except this server's own logs.
     */
    public boolean saveLocationToRedis(Location location){
        try{
            String key = REDIS_KEY + location.getRide().getId();
            String json = objectMapper.writeValueAsString(location);

            redisTemplate.opsForList().rightPush(key, json);
            log.info("Saved location to Redis | key={}", key);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize location: {}", e.getMessage());
            return false;
        } catch (Exception redisUnavailable) {
            log.error("Redis unavailable, could not save location for ride {}: {}",
                    location.getRide().getId(), redisUnavailable.getMessage());
            return false;
        }
    }

    public List<Location> getLocationsFromRedis(Long rideId){
        String key = REDIS_KEY + rideId;
        List<Location> locations = new ArrayList<>();
        List<String> jsonList;

        try {
            jsonList = redisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception redisUnavailable) {
            log.error("Redis unavailable, could not read locations for ride {}: {}", rideId, redisUnavailable.getMessage());
            return locations; // empty — caller falls back to whatever's in MySQL
        }

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
        try {
            String key = REDIS_KEY + rideId;
            redisTemplate.delete(key);
            log.info("Cleared Redis key={}", key);
        } catch (Exception redisUnavailable) {
            // Nothing to clear if Redis is unreachable — the flush this
            // follows already handled getting the data into MySQL (or
            // fell back gracefully if it couldn't), so this is safe to skip.
            log.error("Redis unavailable, could not clear locations for ride {}: {}", rideId, redisUnavailable.getMessage());
        }
    }

    public Set<String> getAllLocationKeys() {

        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(REDIS_KEY + "*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            System.err.println("Redis SCAN error: " + e.getMessage());
        }

        return keys;
    }

    public Location getLastLocationFromRedis(Long rideId) {
        String key = REDIS_KEY + rideId;
        String json;
        try {
            // rightPop index -1 = last element (most recent)
            json = redisTemplate.opsForList().index(key, -1);
        } catch (Exception redisUnavailable) {
            log.error("Redis unavailable, could not read last location for ride {}: {}", rideId, redisUnavailable.getMessage());
            return null; // caller falls back to MySQL
        }
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Location.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize last location from Redis: {}", e.getMessage());
            return null;
        }
    }
}