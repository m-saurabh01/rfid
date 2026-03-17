package com.rfid.integration.lifecycle;

import com.rfid.integration.core.ReaderRegistry;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class AppShutdownListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

        ReaderRegistry.getInstance()
                .getAll()
                .forEach(node -> node.shutdown());
    }
}