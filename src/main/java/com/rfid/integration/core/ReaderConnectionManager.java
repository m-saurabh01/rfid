package com.rfid.integration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages RFID reader lifecycle and WebSocket session counting.
 *
 * REWRITTEN — Critical changes:
 *
 * 1. SPRING @Service: Replaces hand-rolled singleton + separate AppShutdownListener.
 *    Spring manages the bean lifecycle. @PreDestroy replaces the external shutdown hook.
 *
 * 2. MERGED ReaderRegistry: The old ReaderRegistry was a redundant facade that delegated
 *    everything to ReaderConnectionManager. All functionality is consolidated here.
 *
 * 3. SESSION ↔ READER LIFECYCLE COUPLING (was completely missing):
 *    - onSessionOpened() increments session count for a reader
 *    - onSessionClosed() decrements count; when it reaches 0, the reader is shut down
 *    This ensures: first session → reader created. Last session closed → full cleanup.
 *    No more zombie readers running after all clients disconnect.
 *
 * 4. LIGHTWEIGHT CREATION: getOrCreate() creates a ReaderNode with no side effects
 *    (no threads, no scheduled tasks). Threads only start on activate() via startInventory().
 *    The old code started 3 threads per reader on construction — even for a status check.
 *
 * 5. SHARED EXECUTORS: ReaderNode receives the shared scheduler and worker pool
 *    instead of creating per-reader ExecutorService instances.
 */
@Service
public class ReaderConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ReaderConnectionManager.class);

    private final ConcurrentHashMap<String, ReaderNode> readers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> sessionCounts = new ConcurrentHashMap<>();

    private final ScheduledExecutorService rfidScheduler;
    private final ExecutorService rfidWorkerPool;
    private final TagEventDispatcher tagEventDispatcher;

    public ReaderConnectionManager(@Qualifier("rfidScheduler") ScheduledExecutorService rfidScheduler,
                                   @Qualifier("rfidWorkerPool") ExecutorService rfidWorkerPool,
                                   TagEventDispatcher tagEventDispatcher) {
        this.rfidScheduler = rfidScheduler;
        this.rfidWorkerPool = rfidWorkerPool;
        this.tagEventDispatcher = tagEventDispatcher;
    }

    /**
     * Get or create a reader node. Lightweight — no threads started until activate().
     */
    public ReaderNode getOrCreate(String ip) {
        return readers.computeIfAbsent(ip, key ->
                new ReaderNode(key, rfidScheduler, rfidWorkerPool, tagEventDispatcher));
    }

    /**
     * Get existing reader, or null. Does NOT create — safe for status checks.
     * Fixes the old getOrCreate() side effect where a GET /reader/status created a reader.
     */
    public ReaderNode get(String ip) {
        return readers.get(ip);
    }

    public Collection<ReaderNode> getAll() {
        return readers.values();
    }

    /**
     * Called when a WebSocket session opens for a reader.
     */
    public void onSessionOpened(String readerIp) {
        int count = sessionCounts
                .computeIfAbsent(readerIp, k -> new AtomicInteger(0))
                .incrementAndGet();
        log.info("Session opened for reader {}. Active sessions: {}", readerIp, count);
    }

    /**
     * Called when a WebSocket session closes.
     * When session count drops to 0, the reader is fully shut down and removed.
     * This is the critical lifecycle coupling that was missing in the original design.
     */
    public void onSessionClosed(String readerIp) {
        if (readerIp == null) return;
        AtomicInteger count = sessionCounts.get(readerIp);
        if (count != null && count.decrementAndGet() <= 0) {
            log.info("No active sessions for reader {}. Shutting down.", readerIp);
            sessionCounts.remove(readerIp);
            removeReader(readerIp);
        } else if (count != null) {
            log.info("Session closed for reader {}. Remaining sessions: {}", readerIp, count.get());
        }
    }

    public void removeReader(String readerIp) {
        ReaderNode node = readers.remove(readerIp);
        if (node != null) {
            node.shutdown();
        }
    }

    /**
     * Shutdown all readers. Called by Spring on context shutdown.
     * Replaces the old AppShutdownListener.
     */
    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all readers ({} total)", readers.size());
        readers.values().forEach(ReaderNode::shutdown);
        readers.clear();
        sessionCounts.clear();
    }
}
