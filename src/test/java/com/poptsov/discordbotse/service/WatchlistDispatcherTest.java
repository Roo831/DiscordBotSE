package com.poptsov.discordbotse.service;

import com.poptsov.discordbotse.model.WatchNotification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WatchlistDispatcherTest {
    @TempDir Path directory;

    private WatchNotification notification(String id) {
        return new WatchNotification(id, "subscription-" + id, "Roo", "42", "100", Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    void retriesOnlyAcknowledgementAfterSuccessfulSendAndBotRestart() {
        var backend = mock(BackendAnalyticsService.class);
        when(backend.getPendingNotifications()).thenReturn(List.of(notification("event-1")));
        doThrow(new IllegalStateException("backend unavailable")).doNothing()
                .when(backend).acknowledgeNotification("event-1");
        var sends = new AtomicInteger();
        var receipts = directory.resolve("receipts.properties");
        new WatchlistDispatcher(backend, new DeliveryReceipts(receipts), event -> {
            sends.incrementAndGet();
            return "555";
        }).poll();
        assertEquals("555", new DeliveryReceipts(receipts).get("event-1"));

        new WatchlistDispatcher(backend, new DeliveryReceipts(receipts), event -> {
            sends.incrementAndGet();
            return "666";
        }).poll();

        assertEquals(1, sends.get());
        verify(backend, times(2)).acknowledgeNotification("event-1");
        assertNull(new DeliveryReceipts(receipts).get("event-1"));
    }

    @Test
    void failedDeliveryIsNotAcknowledgedAndDoesNotBlockOtherSubscribers() {
        var backend = mock(BackendAnalyticsService.class);
        when(backend.getPendingNotifications()).thenReturn(List.of(notification("bad"), notification("good")));
        var dispatcher = new WatchlistDispatcher(backend, new DeliveryReceipts(directory.resolve("receipts.properties")), event -> {
            if (event.eventId().equals("bad")) throw new IllegalStateException("missing channel");
            return "555";
        });
        dispatcher.poll();
        verify(backend, never()).acknowledgeNotification("bad");
        verify(backend).acknowledgeNotification("good");
    }

    @Test
    void failedSendCanSucceedOnTheNextPoll() {
        var backend = mock(BackendAnalyticsService.class);
        when(backend.getPendingNotifications()).thenReturn(List.of(notification("retry")));
        var attempts = new AtomicInteger();
        var dispatcher = new WatchlistDispatcher(backend, new DeliveryReceipts(directory.resolve("receipts.properties")), event -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Discord unavailable");
            return "555";
        });
        dispatcher.poll();
        verify(backend, never()).acknowledgeNotification(anyString());
        dispatcher.poll();
        assertEquals(2, attempts.get());
        verify(backend).acknowledgeNotification("retry");
    }

    @Test
    void aFullPageOfFailedDeliveriesDoesNotStarveTheNextPage() {
        var backend = mock(BackendAnalyticsService.class);
        var firstPage = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> notification("failed-" + i)).toList();
        when(backend.getPendingNotifications()).thenReturn(firstPage);
        var cursor = firstPage.getLast();
        when(backend.getPendingNotifications(cursor.observedAt(), cursor.eventId()))
                .thenReturn(List.of(notification("good")));
        var sent = new AtomicInteger();
        new WatchlistDispatcher(backend, new DeliveryReceipts(directory.resolve("receipts.properties")), event -> {
            if (event.eventId().startsWith("failed-")) throw new IllegalStateException("channel unavailable");
            sent.incrementAndGet();
            return "555";
        }).poll();
        assertEquals(1, sent.get());
        verify(backend).acknowledgeNotification("good");
    }
}
