package com.poptsov.discordbotse.service;

import com.poptsov.discordbotse.model.WatchNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class WatchlistDispatcher {
    private static final Logger log = LoggerFactory.getLogger(WatchlistDispatcher.class);
    private final BackendAnalyticsService backend;
    private final DeliveryReceipts receipts;
    private final Function<WatchNotification, String> sender;

    public WatchlistDispatcher(BackendAnalyticsService backend, DeliveryReceipts receipts,
                              Function<WatchNotification, String> sender) {
        this.backend = backend;
        this.receipts = receipts;
        this.sender = sender;
    }

    public synchronized void poll() {
        try {
            var batch = backend.getPendingNotifications();
            while (!batch.isEmpty()) {
                for (var event : batch) {
                    try {
                        String messageId = receipts.get(event.eventId());
                        if (messageId == null) messageId = sender.apply(event);
                        // shortcut: a crash before this durable write can repeat a send; Discord has no transactional ack.
                        receipts.remember(event.eventId(), messageId);
                        backend.acknowledgeNotification(event.eventId());
                        receipts.forget(event.eventId());
                    } catch (RuntimeException e) {
                        log.warn("Notification {} remains pending: {}", event.eventId(), e.getMessage());
                    }
                }
                if (batch.size() < 100) break;
                var cursor = batch.getLast();
                batch = backend.getPendingNotifications(cursor.observedAt(), cursor.eventId());
            }
        } catch (RuntimeException e) {
            log.warn("Cannot poll watchlist: {}", e.getMessage());
        }
    }
}
