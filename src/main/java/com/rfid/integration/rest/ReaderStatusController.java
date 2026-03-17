package com.rfid.integration.rest;

import com.rfid.integration.core.ReaderConnectionManager;
import com.rfid.integration.core.ReaderNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

/**
 * REST endpoint for reader status inquiry.
 *
 * CHANGED:
 * - Uses ReaderConnectionManager.get() instead of getOrCreate():
 *   Status check no longer creates a reader + starts 3 threads as a side effect.
 *   If reader doesn't exist, returns connected=false, inventoryRunning=false.
 * - IP validation via regex
 * - Constructor injection of Spring-managed ReaderConnectionManager
 * - Path uses {ip:.+} to prevent dot truncation
 */
@RestController
@RequestMapping("/reader")
public class ReaderStatusController {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    private final ReaderConnectionManager connectionManager;

    public ReaderStatusController(ReaderConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @GetMapping("/status/{ip:.+}")
    public ResponseEntity<String> status(@PathVariable String ip) {
        if (!IPV4_PATTERN.matcher(ip).matches()) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid IP address\"}");
        }

        ReaderNode node = connectionManager.get(ip);
        if (node == null) {
            return ResponseEntity.ok("{\"connected\":false,\"inventoryRunning\":false}");
        }

        String json = "{\"connected\":" + node.isConnected()
                + ",\"inventoryRunning\":" + node.isInventoryRunning() + "}";

        return ResponseEntity.ok(json);
    }
}
