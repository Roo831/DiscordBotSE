package com.poptsov.discordbotse.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class WatchlistApiClientTest {
    @Test
    void decodesSubscriptionsPendingAndSimilarityAndEncodesOwnerAndCursorParameters() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new ArrayList<String>();
        var parameters = new ConcurrentHashMap<String, String>();
        String watch = """
                {"id":"watch-1","nickname":"Roo + & Шкет","userId":"42","channelId":"100",
                "createdAt":"2026-10-12T10:00:00Z","status":"WAITING"}
                """;
        server.createContext("/api/v1/analytics/rust", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            calls.add(method + " " + path);
            parameters.put("apiKey", exchange.getRequestHeaders().getFirst("X-API-KEY"));
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) for (String parameter : query.split("&")) {
                String[] pair = parameter.split("=", 2);
                parameters.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            String json;
            if (path.endsWith("/similar")) json = """
                    {"nickname":"Roo","days":7,"periodStart":"2026-10-05T10:00:00","periodEnd":"2026-10-12T10:00:00",
                    "timeZone":"UTC","totalSeconds":7200,"players":[{"nickname":"Шкет","totalSeconds":7200,
                    "overlapSeconds":3600,"sharedDays":1,"targetOverlapPercent":50.0,"playerOverlapPercent":50.0,
                    "similarityPercent":33.3333,"sharedSessions":3,"rankingScore":99.9999}]}
                    """;
            else if (path.endsWith("/pending")) json = """
                    [{"eventId":"event-1","subscriptionId":"watch-1","nickname":"Roo","userId":"42",
                    "channelId":"100","observedAt":"2026-10-12T10:00:00Z"}]
                    """;
            else if (method.equals("DELETE") || path.endsWith("/ack")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            } else json = method.equals("POST") ? "{\"subscription\":" + watch + ",\"alreadyOnline\":false}" : "[" + watch + "]";
            if (path.endsWith("/similar") && "Legacy".equals(parameters.get("nickname"))) {
                json = json.replace(",\"sharedSessions\":3,\"rankingScore\":99.9999", "");
            }
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var backend = new BackendAnalyticsService("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/v1/analytics", "test-key", "/rust");
            var registered = backend.watch("Roo + & Шкет", "42", "100");
            assertEquals("Roo + & Шкет", parameters.get("nickname"));
            assertEquals("watch-1", registered.subscription().id());
            assertFalse(registered.alreadyOnline());
            assertEquals(Instant.parse("2026-10-12T10:00:00Z"), registered.subscription().createdAt());
            assertEquals("WAITING", backend.getWatchlist("42", "100").getFirst().status());
            var pending = backend.getPendingNotifications().getFirst();
            backend.getPendingNotifications(pending.observedAt(), pending.eventId());
            assertEquals("2026-10-12T10:00:00Z", parameters.get("afterObservedAt"));
            assertEquals("event-1", parameters.get("afterEventId"));
            var similar = backend.getSimilarActivity("Roo + & Шкет", 7);
            assertEquals(3600, similar.players().getFirst().overlapSeconds());
            assertEquals(33.3333, similar.players().getFirst().similarityPercent(), 0.00001);
            assertEquals(3, similar.players().getFirst().sharedSessions());
            assertEquals(99.9999, similar.players().getFirst().rankingScore(), 0.00001);
            backend.unwatch("watch-1", "42", "100");
            backend.acknowledgeNotification("event-1");
            assertTrue(calls.contains("DELETE /api/v1/analytics/rust/watchlist/watch-1"));
            assertTrue(calls.contains("POST /api/v1/analytics/rust/watchlist/pending/event-1/ack"));
            assertEquals("42", parameters.get("userId"));
            assertEquals("100", parameters.get("channelId"));
            assertEquals("test-key", parameters.get("apiKey"));
            var legacy = backend.getSimilarActivity("Legacy", 7).players().getFirst();
            assertNull(legacy.sharedSessions());
            assertNull(legacy.rankingScore());
        } finally {
            server.stop(0);
        }
    }
}
