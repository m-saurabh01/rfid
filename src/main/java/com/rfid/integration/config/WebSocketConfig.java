package com.rfid.integration.config;

import com.rfid.integration.websocket.RfidEndpoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw WebSocket handler at /rfid-stream.
 * No STOMP, no SockJS — clients use: new WebSocket("ws://host/rfid-stream?readerIp=x.x.x.x")
 *
 * CHANGED:
 * - Constructor injection instead of @Autowired field injection
 * - Removed old ServerEndpointExporter bean (not needed with TextWebSocketHandler)
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RfidEndpoint rfidEndpoint;

    public WebSocketConfig(RfidEndpoint rfidEndpoint) {
        this.rfidEndpoint = rfidEndpoint;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rfidEndpoint, "/rfid-stream")
                .setAllowedOrigins("*");
    }
}
