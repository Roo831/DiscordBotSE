package com.poptsov.discordbotse.service;

import com.poptsov.discordbotse.model.PlayerActivityReport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BackendAnalyticsServiceTest {
    @Test
    void decodesJsonReportsAndPreservesSpecialCharactersInNicknames() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> nickname = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        server.createContext("/api/v1/analytics/rust/stats", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-KEY"));
            for (String parameter : exchange.getRequestURI().getRawQuery().split("&")) {
                String[] parts = parameter.split("=", 2);
                if (parts[0].equals("nickname")) nickname.set(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
            byte[] body = """
                    {"nickname":"Alice + Bob & Co","days":7,"periodStart":"2026-10-02T12:00:00",
                     "periodEnd":"2026-10-09T12:00:00","timeZone":"UTC","totalSeconds":5400,
                     "peakHour":10,"dailyActivity":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            BackendAnalyticsService client = new BackendAnalyticsService(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/analytics", "test-key", "/rust");
            Object result = client.getPlayerReport("Alice + Bob & Co", 7);
            assertInstanceOf(PlayerActivityReport.class, result);
            assertEquals(5400, ((PlayerActivityReport) result).totalSeconds());
            assertEquals("Alice + Bob & Co", nickname.get());
            assertEquals("test-key", apiKey.get());
        } finally {
            server.stop(0);
        }
    }
}
