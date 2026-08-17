package com.expensetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorServiceConfig {

    private static final int THREAD_POOL_SIZE = 10;

    // Fixed thread pool shared by all CSV import requests, shut down cleanly on app context close.
    @Bean(destroyMethod = "shutdown")
    public ExecutorService csvImportExecutor() {
        return Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }
}
