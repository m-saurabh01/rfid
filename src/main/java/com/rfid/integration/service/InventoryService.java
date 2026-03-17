package com.rfid.integration.service;

import com.rfid.integration.core.ReaderConnectionManager;
import com.rfid.integration.core.ReaderNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Business logic facade for reader inventory operations.
 *
 * CHANGED:
 * - Spring @Service with constructor injection (was a POJO instantiated via `new` in controller)
 * - IP validation before any device operation — strict IPv4 regex + blocked ranges
 *   Blocks loopback (127.x), unspecified (0.x), link-local (169.254.x) ranges for SSRF protection
 * - disconnectReader() uses removeReader() which also cleans up the registry entry
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    private final ReaderConnectionManager connectionManager;

    public InventoryService(ReaderConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void startReader(String ip) throws Exception {
        validateIp(ip);
        ReaderNode node = connectionManager.getOrCreate(ip);
        node.startInventory();
    }

    public void stopReader(String ip) {
        validateIp(ip);
        ReaderNode node = connectionManager.get(ip);
        if (node != null) {
            node.stopInventory();
        }
    }

    public void disconnectReader(String ip) {
        validateIp(ip);
        connectionManager.removeReader(ip);
    }

    private void validateIp(String ip) {
        if (ip == null || !IPV4_PATTERN.matcher(ip).matches()) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }
        if (ip.startsWith("127.") || ip.startsWith("0.") || ip.startsWith("169.254.")) {
            throw new IllegalArgumentException("Blocked IP range: " + ip);
        }
    }
}
