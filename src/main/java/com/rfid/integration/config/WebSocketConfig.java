package com.rfid.integration.config;

import com.rfid.integration.websocket.RfidEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw WebSocket handler — no STOMP, no SockJS broker.
 * Clients connect with plain WebSocket API, no external JS library needed.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private RfidEndpoint rfidEndpoint;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rfidEndpoint, "/rfid-stream")
                .setAllowedOrigins("*");
    }
}
