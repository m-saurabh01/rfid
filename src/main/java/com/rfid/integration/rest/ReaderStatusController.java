package com.rfid.integration.rest;

import com.rfid.integration.core.ReaderNode;
import com.rfid.integration.core.ReaderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reader")
public class ReaderStatusController {

    @GetMapping("/status/{ip}")
    public ResponseEntity<String> status(@PathVariable String ip) {

        ReaderNode node = ReaderRegistry.getInstance().getOrCreate(ip);

        String json = "{"
                + "\"connected\":" + node.isConnected() + ","
                + "\"inventoryRunning\":" + node.isInventoryRunning()
                + "}";

        return ResponseEntity.ok(json);
    }
}
