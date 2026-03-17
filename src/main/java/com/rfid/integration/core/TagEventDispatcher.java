package com.rfid.integration.core;

import com.rfid.integration.model.TagDataDTO;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TagEventDispatcher {

    private static final Map<String, Set<WebSocketSession>> readerSessions =
            new ConcurrentHashMap<>();

    private TagEventDispatcher() {}

    public static void register(String readerIp, WebSocketSession session) {
        readerSessions
                .computeIfAbsent(readerIp, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public static void unregister(WebSocketSession session) {
        for (Set<WebSocketSession> sessions : readerSessions.values()) {
            sessions.remove(session);
        }
    }

    public static void broadcast(String readerIp, TagDataDTO tag) {
        Set<WebSocketSession> sessions = readerSessions.get(readerIp);
        if (sessions == null) return;

        TextMessage message = new TextMessage(tag.toJson(readerIp));

        for (WebSocketSession s : sessions) {
            // sendMessage is not thread-safe; synchronize per session
            synchronized (s) {
                if (s.isOpen()) {
                    try {
                        s.sendMessage(message);
                    } catch (IOException e) {
                        System.out.println("Failed to send to session " + s.getId() + ": " + e.getMessage());
                    }
                }
            }
        }
    }
}
