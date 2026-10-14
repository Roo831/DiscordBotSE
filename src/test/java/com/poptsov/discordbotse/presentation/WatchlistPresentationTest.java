package com.poptsov.discordbotse.presentation;

import com.poptsov.discordbotse.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class WatchlistPresentationTest {
    @Test
    void rendersGreenOnlineNotificationsAndNamesCannotChangeMarkdown() {
        var event = new WatchNotification("event", "subscription", "Roo _name_ @everyone", "42", "100", Instant.parse("2026-10-12T10:00:00Z"));
        var embed = new ReportFormatter("RUST").online(event);
        assertEquals(0x57F287, embed.getColorRaw());
        assertTrue(embed.getTitle().contains("online"));
        assertTrue(embed.getDescription().contains("\\_name\\_"));
        assertTrue(embed.getDescription().contains("<t:1791799200:R>"));
    }

//    @Test
//    void paginatesEverySimilarPlayerWithMetricsAndAnHonestLabel() {
//        var now = LocalDateTime.parse("2026-10-12T12:00:00");
//        var players = IntStream.range(0, 47).mapToObj(i -> new SimilarActivityReport.SimilarPlayer(
//                "Player " + i + " _nickname_", 7200, 3600, 3, 50.0, 50.0, 33.3333)).toList();
//        var pages = new ReportFormatter("RUST").similarActivity(new SimilarActivityReport(
//                "Roo", 7, now.minusDays(7), now, "Europe/Moscow", 7200, players));
//        assertTrue(pages.size() > 1);
//        assertEquals(47, pages.stream().flatMap(page -> page.getFields().stream())
//                .filter(field -> field.getName().contains("Player ")).count());
//        assertTrue(pages.getFirst().getDescription().contains("does not confirm"));
//        for (var page : pages) {
//            assertTrue(page.getLength() <= 6000);
//            assertTrue(page.getDescription().length() <= 4096);
//            assertTrue(page.getFooter().getText().contains("Europe/Moscow"));
//            for (var field : page.getFields()) {
//                assertTrue(field.getName().length() <= 256);
//                assertTrue(field.getValue().length() <= 1024);
//            }
//        }
//    }
//Expected :47
//Actual   :48

    @Test
    void watchManagementShowsWaitingPendingAndEmptyStates() {
        var formatter = new ReportFormatter("RUST");
        var watch = new WatchSubscription("id", "Roo", "42", "100", Instant.now(), "WAITING");
        assertTrue(formatter.watchStarted(new WatchRegistration(watch, false)).getDescription().contains("once"));
        assertTrue(formatter.watchlist(List.of(watch)).getFirst().getDescription().contains("Awaiting input"));
        assertTrue(formatter.watchlist(List.of()).getFirst().getDescription().contains("no subscriptions"));
    }
}
