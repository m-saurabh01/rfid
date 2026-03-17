package com.rfid.integration.core;

import com.rfid.integration.device.LLRPReaderAdapter;
import com.rfid.integration.model.TagDataDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a single RFID reader and its lifecycle.
 *
 * REWRITTEN — Critical changes:
 *
 * 1. LAZY ACTIVATION: Constructor is lightweight (no threads, no tasks). Monitoring and
 *    cache cleanup only start on activate(), which is called from startInventory().
 *    This means getOrCreate() and status checks no longer leak threads.
 *
 * 2. SHARED THREAD POOLS: Uses injected ScheduledExecutorService and ExecutorService
 *    instead of creating per-reader Executors. With 100 readers, total thread count stays
 *    at pool size (~12) instead of 300.
 *
 * 3. BOUNDED RETRY: monitorConnection() retries up to MAX_RETRIES times when a connection
 *    drops. After that, it logs an error and stops. No infinite reconnect loops.
 *    Uses fixed interval (appropriate for RFID readers on LAN — not internet-scale backoff).
 *
 * 4. TAG CACHE TTL: cleanupTagCache() runs every 30s on the shared scheduler. Entries older
 *    than TAG_CACHE_TTL_MS (60s) are evicted. Inline eviction at TAG_CACHE_CLEANUP_THRESHOLD
 *    (10k entries) prevents unbounded growth between cleanup cycles.
 *
 * 5. NO WORKER THREAD: Tag broadcast is submitted directly to shared workerPool via
 *    onTagReceived(). Eliminates the per-reader BlockingQueue + consumer thread.
 *
 * 6. PROPER SHUTDOWN: Cancels ScheduledFutures (not shutdownNow on shared pools), clears
 *    cache, disconnects adapter. shutdownRequested flag prevents post-shutdown work.
 *
 * 7. REMOVED DUPLICATE MONITORS: Old code had two identical scheduled tasks
 *    (ensureConnection + checkConnection) doing the same thing. Now one monitor task.
 */
public class ReaderNode {

    private static final Logger log = LoggerFactory.getLogger(ReaderNode.class);

    private final String readerIp;
    private final LLRPReaderAdapter adapter;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerPool;
    private final TagEventDispatcher tagEventDispatcher;

    private volatile boolean connected = false;
    private volatile boolean inventoryRunning = false;
    private volatile boolean activated = false;
    private volatile boolean shutdownRequested = false;

    private ScheduledFuture<?> monitorFuture;
    private ScheduledFuture<?> cacheCleanupFuture;

    private final ConcurrentHashMap<String, Long> tagCache = new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    private static final int MAX_RETRIES = 5;
    private static final long MONITOR_INTERVAL_SECONDS = 15;
    private static final long TAG_DEDUP_WINDOW_MS = 500;
    private static final long TAG_CACHE_TTL_MS = 60_000;
    private static final int TAG_CACHE_CLEANUP_THRESHOLD = 10_000;

    public ReaderNode(String readerIp,
                      ScheduledExecutorService scheduler,
                      ExecutorService workerPool,
                      TagEventDispatcher tagEventDispatcher) {
        this.readerIp = readerIp;
        this.scheduler = scheduler;
        this.workerPool = workerPool;
        this.tagEventDispatcher = tagEventDispatcher;
        this.adapter = new LLRPReaderAdapter(readerIp, this::onTagReceived);
    }

    /**
     * Starts monitoring and cache cleanup tasks on the shared scheduler.
     * Called only when the first session connects and inventory is requested.
     * Idempotent — safe to call multiple times.
     */
    public synchronized void activate() {
        if (activated || shutdownRequested) return;

        monitorFuture = scheduler.scheduleWithFixedDelay(
                this::monitorConnection, MONITOR_INTERVAL_SECONDS, MONITOR_INTERVAL_SECONDS, TimeUnit.SECONDS);

        cacheCleanupFuture = scheduler.scheduleWithFixedDelay(
                this::cleanupTagCache, 30, 30, TimeUnit.SECONDS);

        activated = true;
        log.info("ReaderNode activated for {}", readerIp);
    }

    // ======================== CONNECTION ========================

    public synchronized void connect() throws Exception {
        if (connected || shutdownRequested) return;
        adapter.connect();
        connected = true;
        consecutiveFailures.set(0);
    }

    /**
     * Periodic monitor — runs on shared scheduler (not a dedicated thread).
     * Detects dropped connections and attempts bounded reconnect up to MAX_RETRIES.
     * After max retries, stops trying. Manual restart via REST is required.
     */
    private void monitorConnection() {
        if (shutdownRequested || !activated) return;

        try {
            // Detect connection drop
            if (connected && !adapter.isConnected()) {
                connected = false;
                log.warn("Connection lost to reader {}", readerIp);
            }

            // Attempt reconnect only if inventory should be running
            if (!connected && inventoryRunning) {
                int failures = consecutiveFailures.incrementAndGet();
                if (failures > MAX_RETRIES) {
                    log.error("Max reconnect attempts ({}) exceeded for reader {}. Manual restart required.",
                            MAX_RETRIES, readerIp);
                    return;
                }

                log.info("Reconnect attempt {}/{} for reader {}", failures, MAX_RETRIES, readerIp);
                adapter.connect();
                connected = true;
                adapter.startInventory();
                consecutiveFailures.set(0);
                log.info("Reconnected to reader {}", readerIp);
            }
        } catch (Exception e) {
            log.warn("Monitor cycle failed for {}: {}", readerIp, e.getMessage());
        }
    }

    // ======================== INVENTORY ========================

    public synchronized void startInventory() throws Exception {
        if (inventoryRunning || shutdownRequested) return;
        if (!activated) activate();
        connect();
        adapter.startInventory();
        inventoryRunning = true;
    }

    public synchronized void stopInventory() {
        if (!inventoryRunning) return;
        try {
            adapter.stopInventory();
        } catch (Exception e) {
            log.debug("Stop inventory failed for {}: {}", readerIp, e.getMessage());
        }
        inventoryRunning = false;
    }

    // ======================== DISCONNECT ========================

    public synchronized void disconnect() {
        try {
            stopInventory();
            adapter.disconnect();
        } catch (Exception e) {
            log.debug("Disconnect failed for {}: {}", readerIp, e.getMessage());
        }
        connected = false;
    }

    // ======================== TAG HANDLING ========================

    /**
     * Called from LLRP IO thread when a tag is detected.
     * Performs dedup check (ConcurrentHashMap lookup — fast), then submits broadcast
     * to shared workerPool. No blocking queue, no dedicated consumer thread.
     *
     * Inline eviction prevents tagCache from growing beyond TAG_CACHE_CLEANUP_THRESHOLD
     * between the 30-second periodic cleanup cycles.
     */
    private void onTagReceived(TagDataDTO tag) {
        if (shutdownRequested) return;

        long now = System.currentTimeMillis();
        Long lastSeen = tagCache.get(tag.getEpc());
        if (lastSeen != null && now - lastSeen < TAG_DEDUP_WINDOW_MS) {
            return;
        }
        tagCache.put(tag.getEpc(), now);

        if (tagCache.size() > TAG_CACHE_CLEANUP_THRESHOLD) {
            evictStaleEntries(now);
        }

        workerPool.execute(() -> tagEventDispatcher.broadcast(readerIp, tag));
    }

    private void evictStaleEntries(long now) {
        Iterator<Map.Entry<String, Long>> it = tagCache.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > TAG_CACHE_TTL_MS) {
                it.remove();
            }
        }
    }

    private void cleanupTagCache() {
        if (shutdownRequested) return;
        int before = tagCache.size();
        evictStaleEntries(System.currentTimeMillis());
        int removed = before - tagCache.size();
        if (removed > 0) {
            log.debug("Tag cache cleanup for {}: removed {} entries, {} remaining",
                    readerIp, removed, tagCache.size());
        }
    }

    // ======================== SHUTDOWN ========================

    /**
     * Full cleanup. Cancels scheduled tasks on the shared pool (does NOT shutdownNow
     * the pool — other readers use it). Disconnects adapter, clears cache.
     * Idempotent via shutdownRequested flag.
     */
    public void shutdown() {
        if (shutdownRequested) return;
        shutdownRequested = true;

        if (monitorFuture != null) monitorFuture.cancel(false);
        if (cacheCleanupFuture != null) cacheCleanupFuture.cancel(false);

        try {
            disconnect();
        } catch (Exception e) {
            log.debug("Shutdown disconnect failed for {}: {}", readerIp, e.getMessage());
        }

        tagCache.clear();
        log.info("ReaderNode shut down for {}", readerIp);
    }

    // ======================== GETTERS ========================

    public String getReaderIp() { return readerIp; }
    public boolean isConnected() { return connected; }
    public boolean isInventoryRunning() { return inventoryRunning; }
}
