package com.rfid.integration.websocket;

import com.rfid.integration.core.ReaderConnectionManager;
import com.rfid.integration.core.TagEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * WebSocket handler for RFID tag streaming.
 * Clients connect with: new WebSocket("ws://host/rfid-stream?readerIp=x.x.x.x")
 *
 * REWRITTEN — Critical changes:
 *
 * 1. SESSION ↔ READER LIFECYCLE COUPLING (was completely missing):
 *    - afterConnectionEstablished() → registers session + calls connectionManager.onSessionOpened()
 *    - afterConnectionClosed() → unregisters session + calls connectionManager.onSessionClosed()
 *    When last session for a reader closes, onSessionClosed() triggers full reader shutdown.
 *
 * 2. IP VALIDATION: Rejects WebSocket connections with invalid/missing readerIp parameter.
 *    Session is immediately closed with BAD_DATA status.
 *
 * 3. SESSION ATTRIBUTES: Stores readerIp in session attributes so it's available in
 *    afterConnectionClosed() without re-parsing the URI. Reliable because
 *    session.getUri() could theoretically return null after close.
 */
@Component
public class RfidEndpoint extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RfidEndpoint.class);
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );
    private static final String ATTR_READER_IP = "readerIp";

    private final TagEventDispatcher tagEventDispatcher;
    private final ReaderConnectionManager connectionManager;

    public RfidEndpoint(TagEventDispatcher tagEventDispatcher,
                        ReaderConnectionManager connectionManager) {
        this.tagEventDispatcher = tagEventDispatcher;
        this.connectionManager = connectionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String readerIp = extractReaderIp(session);

        if (readerIp == null || !IPV4_PATTERN.matcher(readerIp).matches()) {
            log.warn("WebSocket rejected: invalid readerIp '{}'", readerIp);
            session.close(CloseStatus.BAD_DATA.withReason("Invalid or missing readerIp parameter"));
            return;
        }

        session.getAttributes().put(ATTR_READER_IP, readerIp);
        tagEventDispatcher.register(readerIp, session);
        connectionManager.onSessionOpened(readerIp);
        log.info("WebSocket session {} opened for reader {}", session.getId(), readerIp);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String readerIp = (String) session.getAttributes().get(ATTR_READER_IP);
        if (readerIp == null) return; // was rejected during open — nothing to clean up

        tagEventDispatcher.unregister(session);
        connectionManager.onSessionClosed(readerIp);
        log.info("WebSocket session {} closed for reader {} (status: {})",
                session.getId(), readerIp, status);
    }

    private String extractReaderIp(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;

        for (String param : uri.getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "readerIp".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
