package com.rfid.integration.rest;

import com.rfid.integration.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for reader control.
 *
 * CHANGED:
 * - InventoryService injected via Spring constructor injection (was `new InventoryService()`)
 * - IP path uses {ip:.+} regex to prevent Spring MVC from truncating after the last dot
 * - Proper error handling: IllegalArgumentException → 400, Exception → 500
 * - Error responses use static messages (not interpolated exception text) to prevent injection
 * - SLF4J logging on failures
 */
@RestController
@RequestMapping("/reader")
public class ReaderController {

    private static final Logger log = LoggerFactory.getLogger(ReaderController.class);

    private final InventoryService service;

    public ReaderController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/start/{ip:.+}")
    public ResponseEntity<String> start(@PathVariable String ip) {
        try {
            service.startReader(ip);
            return ResponseEntity.ok("{\"status\":\"started\"}");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid start request for '{}': {}", ip, e.getMessage());
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid IP address\"}");
        } catch (Exception e) {
            log.error("Failed to start reader {}: {}", ip, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to start reader\"}");
        }
    }

    @PostMapping("/stop/{ip:.+}")
    public ResponseEntity<String> stop(@PathVariable String ip) {
        try {
            service.stopReader(ip);
            return ResponseEntity.ok("{\"status\":\"stopped\"}");
        } catch (IllegalArgumentException e) {
            log.warn("Invalid stop request for '{}': {}", ip, e.getMessage());
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid IP address\"}");
        } catch (Exception e) {
            log.error("Failed to stop reader {}: {}", ip, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to stop reader\"}");
        }
    }
}
