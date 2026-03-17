package com.rfid.integration.websocket;

import com.rfid.integration.core.TagEventDispatcher;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/rfid-stream")
public class RfidEndpoint {

    @OnOpen
    public void onOpen(Session session) {

        TagEventDispatcher.register(session);
    }

    @OnClose
    public void onClose(Session session) {

        TagEventDispatcher.unregister(session);
    }
}