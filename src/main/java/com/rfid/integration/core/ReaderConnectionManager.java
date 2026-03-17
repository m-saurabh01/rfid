package com.rfid.integration.core;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ReaderConnectionManager {

    private static final ReaderConnectionManager INSTANCE =
            new ReaderConnectionManager();

    private final ConcurrentHashMap<String, ReaderNode> readers =
            new ConcurrentHashMap<>();

    private ReaderConnectionManager() {}

    public static ReaderConnectionManager getInstance() {
        return INSTANCE;
    }

    public ReaderNode getOrCreateReader(String ip) {
        return readers.computeIfAbsent(ip, ReaderNode::new);
    }

    public ReaderNode getReader(String ip) {
        return readers.get(ip);
    }

    public Collection<ReaderNode> getAllReaders() {
        return readers.values();
    }

    public void removeReader(String ip) {
        ReaderNode node = readers.remove(ip);
        if (node != null) {
            node.shutdown();
        }
    }

    public void shutdownAll() {
        readers.values().forEach(ReaderNode::shutdown);
    }
}
