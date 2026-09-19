package com.deep.WIMB.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // same public data as /api/ride/active/all already serves
                .withSockJS();
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
