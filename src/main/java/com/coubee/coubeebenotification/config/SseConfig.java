package com.coubee.coubeebenotification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class SseConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // SSE를 위한 비동기 설정
        configurer.setDefaultTimeout(30 * 60 * 1000); // 30분 타임아웃
        
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setThreadNamePrefix("sse-async-");
        executor.setDaemon(true);
        configurer.setTaskExecutor(executor);
        
        log.info("SSE async support configured with 30 minute timeout");
    }
}