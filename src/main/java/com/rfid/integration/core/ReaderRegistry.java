package com.rfid.integration.core;

import java.util.Collection;

public class ReaderRegistry {

    private static final ReaderRegistry INSTANCE = new ReaderRegistry();

    private final ReaderConnectionManager connectionManager =
            ReaderConnectionManager.getInstance();

    private ReaderRegistry() {}

    public static ReaderRegistry getInstance() {
        return INSTANCE;
    }

    public ReaderNode getOrCreate(String ip) {
        return connectionManager.getOrCreateReader(ip);
    }

    public ReaderNode get(String ip) {
        return connectionManager.getReader(ip);
    }

    public Collection<ReaderNode> getAll() {
        return connectionManager.getAllReaders();
    }
}
