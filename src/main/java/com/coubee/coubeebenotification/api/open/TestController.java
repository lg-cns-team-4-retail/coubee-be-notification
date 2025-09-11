package com.coubee.coubeebenotification.api.open;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notification/test")
public class TestController {

    @GetMapping(value = "/simple-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter simpleSse(HttpServletResponse response) {
        log.info("Simple SSE test request received");
        
        // 기본 SSE 헤더 설정
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Content-Type", "text/event-stream; charset=utf-8");
        
        SseEmitter emitter = new SseEmitter(30000L); // 30초 타임아웃
        
        try {
            // 즉시 테스트 메시지 전송
            emitter.send(SseEmitter.event()
                    .name("test")
                    .data("Simple SSE test successful"));
            
            log.info("Simple SSE test message sent");
            
            // 1초 후 자동 종료
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data("Test completed"));
                    emitter.complete();
                    log.info("Simple SSE test completed");
                } catch (Exception e) {
                    log.error("Error in simple SSE test", e);
                    emitter.completeWithError(e);
                }
            }).start();
            
        } catch (Exception e) {
            log.error("Failed to send simple SSE test message", e);
            emitter.completeWithError(e);
        }
        
        return emitter;
    }
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "OK",
                "timestamp", System.currentTimeMillis(),
                "service", "coubee-be-notification"
        );
    }
}