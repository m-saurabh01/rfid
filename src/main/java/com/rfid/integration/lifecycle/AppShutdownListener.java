package com.rfid.integration.lifecycle;

import com.rfid.integration.core.ReaderRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Component
public class AppShutdownListener implements ApplicationListener<ContextClosedEvent> {

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        ReaderRegistry.getInstance()
                .getAll()
                .forEach(node -> node.shutdown());
    }
}
