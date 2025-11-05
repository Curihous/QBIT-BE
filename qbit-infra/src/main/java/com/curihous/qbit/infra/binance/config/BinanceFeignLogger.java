package com.curihous.qbit.infra.binance.config;

import feign.Request;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
public class BinanceFeignLogger extends feign.Logger {

    @Override
    protected void log(String configKey, String format, Object... args) {
        log.debug(String.format(format, args));
    }

    @Override
    protected void logRequest(String configKey, feign.Logger.Level logLevel, Request request) {
        if (logLevel.ordinal() >= feign.Logger.Level.FULL.ordinal()) {
            super.logRequest(configKey, logLevel, request);
            logDetailedRequest(request);
        }
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, feign.Logger.Level logLevel, 
                                              Response response, long elapsedTime) throws IOException {
        Response bufferedResponse = super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
        if (logLevel.ordinal() >= feign.Logger.Level.FULL.ordinal()) {
            logDetailedResponse(bufferedResponse);
        }
        return bufferedResponse;
    }

    private void logDetailedRequest(Request request) {
        StringBuilder logBuilder = new StringBuilder("\n");
        logBuilder.append("=".repeat(80)).append("\n");
        logBuilder.append("Binance Feign Request:\n");
        logBuilder.append("=".repeat(80)).append("\n");
        
        // Request Line
        logBuilder.append(String.format("%s %s %s\n", 
                request.httpMethod(), 
                request.url(), 
                "HTTP/1.1"));
        
        // Headers
        logBuilder.append("Headers:\n");
        if (request.headers() != null) {
            for (Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
                for (String value : entry.getValue()) {
                    logBuilder.append(String.format("  '%s': '%s'\n", entry.getKey(), value));
                }
            }
        }
        
        // Body
        if (request.body() != null && request.body().length > 0) {
            logBuilder.append("\nBody:\n");
            String bodyStr = new String(request.body(), StandardCharsets.UTF_8);
            logBuilder.append(bodyStr).append("\n");
        } else {
            logBuilder.append("\nBody: (없음)\n");
        }
        
        logBuilder.append("=".repeat(80));
        
        log.info(logBuilder.toString());
    }

    private void logDetailedResponse(Response response) {
        StringBuilder logBuilder = new StringBuilder("\n");
        logBuilder.append("=".repeat(80)).append("\n");
        logBuilder.append("Binance Feign Response:\n");
        logBuilder.append("=".repeat(80)).append("\n");
        
        // Status Line
        logBuilder.append(String.format("%s %s %s\n", 
                response.request().httpMethod(),
                response.request().url(),
                response.status()));
        
        // Headers
        logBuilder.append("Headers:\n");
        if (response.headers() != null) {
            for (Map.Entry<String, Collection<String>> entry : response.headers().entrySet()) {
                for (String value : entry.getValue()) {
                    logBuilder.append(String.format("  '%s': '%s'\n", entry.getKey(), value));
                }
            }
        }
        
        // Body (읽기 가능한 경우만 - 이미 super.logAndRebufferResponse에서 읽혔을 수 있음)
        if (response.body() != null) {
            try {
                if (response.body().length() > 0) {
                    logBuilder.append("\nBody:\n");
                    byte[] bodyBytes = new byte[response.body().length()];
                    response.body().asInputStream().read(bodyBytes);
                    String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
                    logBuilder.append(bodyStr).append("\n");
                } else {
                    logBuilder.append("\nBody: (빈 응답)\n");
                }
            } catch (IOException e) {
                logBuilder.append("\nBody: (읽기 실패: ").append(e.getMessage()).append(")\n");
            }
        } else {
            logBuilder.append("\nBody: (없음)\n");
        }
        
        logBuilder.append("=".repeat(80));
        
        log.info(logBuilder.toString());
    }

}

