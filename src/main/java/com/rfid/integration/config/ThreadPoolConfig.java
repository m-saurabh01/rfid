package com.rfid.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Centralized thread pool configuration.
 *
 * WHY: The old design created 3 threads per reader (1 worker + 2 scheduled monitors).
 * With 100 readers = 300 threads competing with Tomcat's ~200 HTTP threads.
 *
 * FIX: Two shared pools managed by Spring with destroyMethod="shutdown":
 *
 * rfidScheduler (ScheduledThreadPoolExecutor, 4 threads):
 *   For periodic monitoring and reconnect tasks. setRemoveOnCancelPolicy(true) ensures
 *   cancelled tasks (from reader shutdown) are immediately purged from the queue instead
 *   of lingering until their scheduled time.
 *
 * rfidWorkerPool (ThreadPoolExecutor, 4-8 threads, queue=512):
 *   For tag broadcast work. CallerRunsPolicy provides natural backpressure — if the queue
 *   fills up, the submitting thread (LLRP IO thread) does the broadcast itself, slowing
 *   down tag ingestion rather than dropping events or growing unboundedly.
 */
@Configuration
public class ThreadPoolConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService rfidScheduler(
            @Value("${rfid.thread-pool.scheduler-size:4}") int poolSize) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(poolSize);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService rfidWorkerPool() {
        return new ThreadPoolExecutor(
                4, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(512),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
