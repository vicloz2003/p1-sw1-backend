package com.ibpms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for {@code @Async} work (FCM push notifications, RF-29/30).
 *
 * <p>Without it, the Spring WebSocket/STOMP auto-configuration registers several
 * {@code TaskExecutor} beans (clientInboundChannelExecutor, brokerChannelExecutor, …)
 * and {@code @Async} cannot pick a default — it logs a warning and falls back to an
 * unmanaged {@code SimpleAsyncTaskExecutor} (a new thread per call). Declaring a bean
 * named {@code taskExecutor} makes it the unambiguous default for all {@code @Async}
 * methods, with a bounded, reusable thread pool.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ibpms-async-");
        executor.initialize();
        return executor;
    }
}
