package com.rfid.integration.service;

import com.rfid.integration.core.ReaderNode;
import com.rfid.integration.core.ReaderRegistry;

public class InventoryService {

    public void startReader(String ip) throws Exception {
        ReaderNode node = ReaderRegistry.getInstance().getOrCreate(ip);
        node.startInventory();
    }

    public void stopReader(String ip) {
        ReaderNode node = ReaderRegistry.getInstance().get(ip);
        if (node != null) {
            node.stopInventory();
        }
    }

    public void disconnectReader(String ip) {
        ReaderNode node = ReaderRegistry.getInstance().get(ip);
        if (node != null) {
            node.disconnect();
        }
    }
}
