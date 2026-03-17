package com.rfid.integration.rest;

import com.rfid.integration.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reader")
public class ReaderController {

    private final InventoryService service = new InventoryService();

    @PostMapping("/start/{ip}")
    public ResponseEntity<Void> start(@PathVariable String ip) throws Exception {
        service.startReader(ip);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop/{ip}")
    public ResponseEntity<Void> stop(@PathVariable String ip) {
        service.stopReader(ip);
        return ResponseEntity.ok().build();
    }
}
