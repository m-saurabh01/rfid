package com.rfid.integration.core;

import com.rfid.integration.model.TagDataDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket session registry and broadcasts tag events.
 *
 * REWRITTEN — Critical changes:
 *
 * 1. Spring @Component (was static utility class with no DI support).
 *
 * 2. ConcurrentWebSocketSessionDecorator wraps every session at registration time.
 *    This provides:
 *    - Thread-safe sendMessage() without manual synchronized blocks
 *    - Send-time limit (5s): if a write takes longer (slow client), the session is closed
 *    - Buffer-size limit (64KB): prevents memory exhaustion from slow consumers
 *    Eliminates the old synchronized(session) pattern that blocked the worker thread.
 *
 * 3. Reverse index (sessionId → readerIp) for O(1) unregister.
 *    Old code iterated ALL reader session sets on every disconnect — O(N) where N = readers.
 *
 * 4. Jackson ObjectMapper for JSON serialization instead of manual string concatenation.
 *    Prevents XSS/injection from EPC strings containing special characters.
 *
 * 5. Dead session cleanup during broadcast (self-healing iteration).
 */
@Component
public class TagEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TagEventDispatcher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Set<WebSocketSession>> readerSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToReader = new ConcurrentHashMap<>();

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    public void register(String readerIp, WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        readerSessions
                .computeIfAbsent(readerIp, k -> ConcurrentHashMap.newKeySet())
                .add(decorated);
        sessionToReader.put(session.getId(), readerIp);
        log.info("Session {} registered for reader {}", session.getId(), readerIp);
    }

    public String unregister(WebSocketSession session) {
        String readerIp = sessionToReader.remove(session.getId());
        if (readerIp != null) {
            Set<WebSocketSession> sessions = readerSessions.get(readerIp);
            if (sessions != null) {
                sessions.removeIf(s -> s.getId().equals(session.getId()));
                if (sessions.isEmpty()) {
                    readerSessions.remove(readerIp);
                }
            }
        }
        log.info("Session {} unregistered from reader {}", session.getId(), readerIp);
        return readerIp;
    }

    public int getSessionCount(String readerIp) {
        Set<WebSocketSession> sessions = readerSessions.get(readerIp);
        return sessions != null ? sessions.size() : 0;
    }

    public void broadcast(String readerIp, TagDataDTO tag) {
        Set<WebSocketSession> sessions = readerSessions.get(readerIp);
        if (sessions == null || sessions.isEmpty()) return;

        tag.setReaderIp(readerIp);
        String json;
        try {
            json = objectMapper.writeValueAsString(tag);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tag data: {}", e.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);
        Iterator<WebSocketSession> it = sessions.iterator();
        while (it.hasNext()) {
            WebSocketSession s = it.next();
            if (!s.isOpen()) {
                it.remove();
                sessionToReader.remove(s.getId());
                continue;
            }
            try {
                // ConcurrentWebSocketSessionDecorator makes this thread-safe
                // with send-time and buffer limits — no manual synchronization needed
                s.sendMessage(message);
            } catch (IOException e) {
                log.warn("Failed to send to session {}, removing: {}", s.getId(), e.getMessage());
                it.remove();
                sessionToReader.remove(s.getId());
                try { s.close(); } catch (IOException ignored) {}
            }
        }
    }
}
