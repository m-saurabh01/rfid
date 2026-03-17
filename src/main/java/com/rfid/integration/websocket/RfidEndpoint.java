package com.rfid.integration.websocket;

import com.rfid.integration.core.TagEventDispatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

/**
 * Spring WebSocket handler — no STOMP, no SockJS.
 * Clients connect with plain: new WebSocket("ws://host/rfid-stream?readerIp=192.168.1.100")
 */
@Component
public class RfidEndpoint extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String readerIp = extractReaderIp(session);
        TagEventDispatcher.register(readerIp, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TagEventDispatcher.unregister(session);
    }

    private String extractReaderIp(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            for (String param : uri.getQuery().split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "readerIp".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return "unknown";
    }
}
