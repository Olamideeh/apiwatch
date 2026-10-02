package com.example.apiwatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class MonitoringSchedulerConfig {

    @Bean
    public ThreadPoolTaskExecutor monitoringExecutor() {
        return createExecutor(4, "apiwatch-check-");
    }

    @Bean
    public ThreadPoolTaskExecutor alertExecutor() {
        return createExecutor(2, "apiwatch-alert-");
    }

    private ThreadPoolTaskExecutor createExecutor(
            int workers,
            String threadPrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(threadPrefix);

        return executor;
    }
}