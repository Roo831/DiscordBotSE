package com.poptsov.discordbotse.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Properties;

/** Receipts survive a restart between a successful Discord send and backend acknowledgement. */
public class DeliveryReceipts {
    private final Path file;
    private final Properties receipts = new Properties();

    public DeliveryReceipts(Path file) {
        this.file = file.toAbsolutePath();
        if (Files.exists(this.file)) {
            try (var input = Files.newInputStream(this.file)) {
                receipts.load(input);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read notification receipts", e);
            }
        }
    }

    public synchronized String get(String eventId) {
        return receipts.getProperty(eventId);
    }

    public synchronized void remember(String eventId, String messageId) {
        receipts.setProperty(eventId, messageId);
        save();
    }

    public synchronized void forget(String eventId) {
        receipts.remove(eventId);
        save();
    }

    private void save() {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "receipts-", ".tmp");
            try (var output = Files.newOutputStream(temporary)) {
                receipts.store(output, "Discord notification receipts");
            }
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save notification receipts", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }
}
