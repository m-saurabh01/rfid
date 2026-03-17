package com.rfid.integration.core;

import com.rfid.integration.model.TagDataDTO;

import javax.websocket.Session;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TagEventDispatcher {

    // thread-safe set of active websocket sessions
   private static final Map<String, Set<Session>> readerSessions =
        new ConcurrentHashMap<>();

    private TagEventDispatcher() {}

    // register session
  public static void register(String readerIp, Session session) {

    readerSessions
        .computeIfAbsent(readerIp, k -> ConcurrentHashMap.newKeySet())
        .add(session);
}

    // remove session
public static void unregister(Session session) {

    for (Set<Session> sessions : readerSessions.values()) {
        sessions.remove(session);
    }
}

    // broadcast tag to all connected clients
    public static void broadcast(String readerIp, TagDataDTO tag) {

    Set<Session> sessions = readerSessions.get(readerIp);

    if (sessions == null) return;

    String json = tag.toJson(readerIp);

    for (Session s : sessions) {

        if (s.isOpen()) {
            s.getAsyncRemote().sendText(json);
        }
    }
}
}