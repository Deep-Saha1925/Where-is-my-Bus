package com.deep.WIMB.scheduler;

import com.deep.WIMB.model.Location;
import com.deep.WIMB.repository.LocationRepository;
import com.deep.WIMB.service.RedisLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationFlushScheduler {

    private final RedisLocationService redisLocationService;
    private final LocationRepository locationRepository;

    // Runs every 60 seconds
    @Scheduled(fixedRate = 60000)
    public void flushLocationsToDB() {
        log.info("Starting scheduled Redis → MySQL flush...");

        Set<String> keys = redisLocationService.getAllLocationKeys();
        if (keys == null || keys.isEmpty()) {
            log.info("No location data in Redis to flush.");
            return;
        }

        for (String key : keys) {
            // Extract rideId from key: "location:ride:5" → 5
            Long rideId = Long.parseLong(key.split(":")[2]);

            List<Location> locations = redisLocationService.getLocationsFromRedis(rideId);
            if (!locations.isEmpty()) {
                locationRepository.saveAll(locations);
                redisLocationService.clearLocationsFromRedis(rideId);
                log.info("Flushed {} locations for rideId={} to MySQL", locations.size(), rideId);
            }
        }
    }
}