package com.rfid.integration.core;

import com.rfid.integration.device.LLRPReaderAdapter;
import com.rfid.integration.model.TagDataDTO;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class ReaderNode {

    private final String readerIp;
    private final LLRPReaderAdapter adapter;

    private final BlockingQueue<TagDataDTO> tagQueue =
            new LinkedBlockingQueue<>(50000);

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();

    private volatile boolean connected = false;
    private volatile boolean inventoryRunning = false;
    
        private final ScheduledExecutorService monitor =
        Executors.newSingleThreadScheduledExecutor();

        private final ConcurrentHashMap<String, Long> tagCache =
        new ConcurrentHashMap<>();



    public ReaderNode(String readerIp) {

        this.readerIp = readerIp;

        Consumer<TagDataDTO> callback = this::enqueueTag;

        this.adapter = new LLRPReaderAdapter(readerIp, callback);
        monitor.scheduleAtFixedRate(this::ensureConnection,
        10, 10, TimeUnit.SECONDS);
        monitor.scheduleAtFixedRate(this::checkConnection,
        10, 10, TimeUnit.SECONDS);

        startWorker();
    }

    // ---------------- CONNECT ----------------

    public synchronized void connect() throws Exception {

        if (connected) return;

        adapter.connect();
        connected = true;
    }

    private void checkConnection() {

    try {

        if (!adapter.isConnected()) {

            System.out.println("Reconnecting reader: " + readerIp);

            adapter.connect();

            if (inventoryRunning) {
                adapter.startInventory();
            }
        }

    } catch (Exception e) {

        System.out.println("Reconnect failed: " + e.getMessage());
    }
}

    // ---------------- START INVENTORY ----------------

    public synchronized void startInventory() throws Exception {

        if (inventoryRunning) return;

        connect();

        adapter.startInventory();

        inventoryRunning = true;
    }

    // ---------------- STOP INVENTORY ----------------

    public synchronized void stopInventory() {

        if (!inventoryRunning) return;

        try {
            adapter.stopInventory();
        } catch (Exception ignored) {}

        inventoryRunning = false;
    }

    // ---------------- DISCONNECT ----------------

    public synchronized void disconnect() {

        try {
            stopInventory();
            adapter.disconnect();
        } catch (Exception ignored) {}

        connected = false;
    }

    // ---------------- TAG QUEUE ----------------

   private void enqueueTag(TagDataDTO tag) {

    long now = System.currentTimeMillis();

    Long lastSeen = tagCache.get(tag.getEpc());

    if (lastSeen != null && now - lastSeen < 500) {
        return; // ignore duplicate within 500 ms
    }

    tagCache.put(tag.getEpc(), now);

    tagQueue.offer(tag);
}

    // ---------------- WORKER ----------------

    private void startWorker() {

        worker.submit(() -> {

            while (!Thread.currentThread().isInterrupted()) {

                TagDataDTO tag = tagQueue.take();

                TagEventDispatcher.broadcast(readerIp, tag);
            }
        });
    }

    // ---------------- SHUTDOWN ----------------

    public void shutdown() {

        try {
            stopInventory();
            disconnect();
        } catch (Exception ignored) {}

        worker.shutdownNow();
    }

    public String getReaderIp() {
        return readerIp;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isInventoryRunning() {
        return inventoryRunning;
    }

    private void ensureConnection() {

    if (!adapter.isConnected()) {

        try {
            adapter.connect();

            if (inventoryRunning) {
                adapter.startInventory();
            }

        } catch (Exception e) {
            System.out.println("Reconnect failed: " + e.getMessage());
        }
    }
}
}