package com.rfid.integration.rest;

import com.rfid.integration.core.TagEventDispatcher;
import com.rfid.integration.model.TagDataDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PreDestroy;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Generates simulated RFID tag events for end-to-end testing without a physical reader.
 * Tags are pushed through the same TagEventDispatcher → WebSocket pipeline as real tags.
 *
 * Usage:
 *   1. Connect WebSocket with any valid IP (e.g. 10.0.0.99)
 *   2. POST /simulate/start/10.0.0.99  → starts generating fake tags
 *   3. POST /simulate/stop/10.0.0.99   → stops
 */
@RestController
@RequestMapping("/simulate")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

    private static final String[] SAMPLE_EPCS = {
            "E200 3411 B802 0116 2690 1C45",
            "E200 3411 B802 0116 2690 1C46",
            "3034 2575 B98C 0040 0000 0003",
            "3034 2575 B98C 0040 0000 0017",
            "E280 1160 6000 0207 8F02 87D5",
            "E280 1160 6000 0207 8F02 87D6",
            "3005 FB63 AC1F 3681 0000 0012",
            "3005 FB63 AC1F 3681 0000 0025",
            "E200 6811 5603 0077 1890 4AE2",
            "E200 6811 5603 0077 1890 4AF3"
    };

    private final TagEventDispatcher tagEventDispatcher;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeSimulations = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public SimulationController(TagEventDispatcher tagEventDispatcher,
                                @Qualifier("rfidScheduler") ScheduledExecutorService scheduler) {
        this.tagEventDispatcher = tagEventDispatcher;
        this.scheduler = scheduler;
    }

    @PostMapping("/start/{ip:.+}")
    public ResponseEntity<String> startSimulation(@PathVariable String ip) {
        if (activeSimulations.containsKey(ip)) {
            return ResponseEntity.ok("{\"status\":\"already running\"}");
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> generateTag(ip), 0, 600, TimeUnit.MILLISECONDS);
        activeSimulations.put(ip, future);

        log.info("Simulation started for {}", ip);
        return ResponseEntity.ok("{\"status\":\"simulation started\"}");
    }

    @PostMapping("/stop/{ip:.+}")
    public ResponseEntity<String> stopSimulation(@PathVariable String ip) {
        ScheduledFuture<?> future = activeSimulations.remove(ip);
        if (future != null) {
            future.cancel(false);
            log.info("Simulation stopped for {}", ip);
        }
        return ResponseEntity.ok("{\"status\":\"simulation stopped\"}");
    }

    private void generateTag(String ip) {
        try {
            String epc = SAMPLE_EPCS[random.nextInt(SAMPLE_EPCS.length)];
            short antenna = (short) (1 + random.nextInt(4));
            short rssi = (short) -(30 + random.nextInt(40)); // -30 to -69 dBm

            TagDataDTO tag = new TagDataDTO(epc, antenna, rssi, System.currentTimeMillis());
            tagEventDispatcher.broadcast(ip, tag);
        } catch (Exception e) {
            log.error("Simulation error: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stopAll() {
        activeSimulations.values().forEach(f -> f.cancel(false));
        activeSimulations.clear();
    }
}
