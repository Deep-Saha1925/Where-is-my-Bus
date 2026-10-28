/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * Replaces passenger/admin polling (track.js was hitting /api/location/last-loc
 * every 3s per open tab, admin-buses.js /api/ride/active/all every 5s) with a
 * single push per real GPS update. Polling load used to scale with the number
 * of people *watching*, not the number of buses actually moving -- ten
 * passengers tracking the same bus meant ten requests every 3 seconds for
 * data that hadn't changed. A WebSocket push only fires when there's
 * something new to say.
 *
 * Two topics:
 *   /topic/ride/{rideId}   -- one bus's location, for track.html
 *   /topic/activeRides     -- the full active-buses list, for admin-buses.html
 *                              and the passenger auto-select flow
 *
 * SockJS is layered on top for browsers/networks that block raw WebSocket
 * (common on some mobile carriers) -- it falls back to HTTP streaming/polling
 * transparently, same public endpoint either way.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Same list as the REST CORS setting (CORS_ALLOWED_ORIGINS). Empty = same-origin only,
    // which is right for production. It used to be "*", letting ANY website open a live
    // socket to this server from a visitor's browser.
    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${wimb.cors.allowed-origins:}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        StompWebSocketEndpointRegistration endpoint = registry.addEndpoint("/ws");
        if (allowedOrigins.length > 0) {
            endpoint.setAllowedOriginPatterns(allowedOrigins);
        }
        endpoint.withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker: fine for a single instance (see the note
        // on ActiveRideCache for what changes if/when this becomes
        // multi-instance -- a real broker like RabbitMQ's STOMP plugin, or
        // Redis pub/sub relayed to STOMP, would be needed so a message
        // published on one instance reaches subscribers connected to another).
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
