package com.poptsov.discordbotse.config;

import com.poptsov.discordbotse.model.PlayerActivityReport;
import com.poptsov.discordbotse.model.PlayerActivityReport.DayActivity;
import com.poptsov.discordbotse.model.PlayerActivityReport.Session;
import com.poptsov.discordbotse.model.NicknameReport;
import com.poptsov.discordbotse.service.BackendAnalyticsService;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscordBotTest {
    @Test
    void showsBackendWatchValidationReasonAndHandlesNonJsonErrors() throws Exception {
        for (boolean jsonError : new boolean[]{true, false}) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/analytics/rust/watchlist", exchange -> {
                byte[] body = (jsonError ? "{\"code\":\"INVALID_REQUEST\",\"message\":\"At most 25 active subscriptions per game.\"}"
                        : "Bad request").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", jsonError ? "application/json" : "text/plain");
                exchange.sendResponseHeaders(400, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            try {
                var backend = new BackendAnalyticsService("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/api/v1/analytics", "test-key", "/rust");
                var bot = new DiscordBot("NOT_SET", "RUST", backend);
                var command = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
                when(command.getName()).thenReturn("rust_watch");
                when(command.isFromGuild()).thenReturn(true);
                when(command.getUser().getId()).thenReturn("42");
                when(command.getChannel().getId()).thenReturn("100");
                when(command.getOption("nickname").getAsString()).thenReturn("Roo");
                var reply = mock(WebhookMessageEditAction.class, RETURNS_SELF);
                when(command.getHook().editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(reply);
                bot.onSlashCommandInteraction(command);
                var embed = ArgumentCaptor.forClass(MessageEmbed.class);
                verify(command.getHook()).editOriginalEmbeds(embed.capture());
                assertEquals(0xED4245, embed.getValue().getColorRaw());
                assertTrue(embed.getValue().getDescription().contains(jsonError ? "25 active subscriptions" : "недоступен"));
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void createsAnOwnerScopedWatchAndConfirmsItPrivately() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var query = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/api/v1/analytics/rust/watchlist", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] json = """
                    {"subscription":{"id":"watch-1","nickname":"Roo + &","userId":"42",
                    "channelId":"100","createdAt":"2026-10-12T10:00:00Z","status":"WAITING"},
                    "alreadyOnline":false}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();
        try {
            var backend = new BackendAnalyticsService("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/v1/analytics", "test-key", "/rust");
            var bot = new DiscordBot("NOT_SET", "RUST", backend);
            var command = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
            when(command.getName()).thenReturn("rust_watch");
            when(command.isFromGuild()).thenReturn(true);
            when(command.getUser().getId()).thenReturn("42");
            when(command.getChannel().getId()).thenReturn("100");
            when(command.getOption("nickname").getAsString()).thenReturn("Roo + &");
            var reply = mock(WebhookMessageEditAction.class, RETURNS_SELF);
            when(command.getHook().editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(reply);

            bot.onSlashCommandInteraction(command);

            var embed = ArgumentCaptor.forClass(MessageEmbed.class);
            verify(command.getHook()).editOriginalEmbeds(embed.capture());
            assertTrue(embed.getValue().getTitle().contains("Слежение"));
            assertTrue(embed.getValue().getDescription().contains("Roo + &"));
            assertNotNull(query.get());
            assertTrue(query.get().contains("userId=42"));
            assertTrue(query.get().contains("channelId=100"));
            verify(command).deferReply(true);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsLargeNicknameJsonAsNavigableColouredEmbeds() throws Exception {
        var now = LocalDateTime.parse("2026-10-12T12:04:01.015157368");
        var names = IntStream.range(0, 500).mapToObj(i -> "Player " + i + " 🚀 _nickname_ + &").toList();
        byte[] json = JsonMapper.builder().build().writeValueAsString(
                new NicknameReport(1, now.minusDays(1), now, "GMT", names)).getBytes(StandardCharsets.UTF_8);
        assertTrue(json.length > 2000);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/analytics/rust/nicknames", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();
        try {
            var backend = new BackendAnalyticsService("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/v1/analytics", "test-key", "/rust");
            var bot = new DiscordBot("NOT_SET", "RUST", backend);
            var command = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
            when(command.getName()).thenReturn("rust_all_nicknames");
            when(command.getUser().getIdLong()).thenReturn(42L);
            when(command.getOption("days").getAsInt()).thenReturn(1);
            var reply = mock(WebhookMessageEditAction.class, RETURNS_SELF);
            when(command.getHook().editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(reply);

            bot.onSlashCommandInteraction(command);

            var firstPage = ArgumentCaptor.forClass(MessageEmbed.class);
            verify(command.getHook()).editOriginalEmbeds(firstPage.capture());
            assertEquals(0xE67E22, firstPage.getValue().getColorRaw());
            assertTrue(firstPage.getValue().getDescription().contains("500 players"));
            assertTrue(firstPage.getValue().getDescription().contains("Player 0"));
            assertTrue(firstPage.getValue().getDescription().length() <= 4096);
            assertTrue(firstPage.getValue().getLength() <= 6000);
            ArgumentCaptor<Collection<Button>> buttons = ArgumentCaptor.captor();
            verify(reply).setActionRow(buttons.capture());
            long players = firstPage.getValue().getDescription().lines().filter(line -> line.startsWith("• ")).count();
            List<Button> navigation = List.copyOf(buttons.getValue());
            for (int pageIndex = 1; !navigation.getLast().isDisabled(); pageIndex++) {
                assertTrue(pageIndex < 20, "Navigation must reach the final page");
                var click = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
                when(click.getComponentId()).thenReturn(navigation.getLast().getId());
                when(click.getUser().getIdLong()).thenReturn(42L);
                var edit = mock(MessageEditCallbackAction.class, RETURNS_SELF);
                when(click.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(edit);
                bot.onButtonInteraction(click);
                var page = ArgumentCaptor.forClass(MessageEmbed.class);
                verify(click).editMessageEmbeds(page.capture());
                assertTrue(page.getValue().getDescription().length() <= 4096);
                assertTrue(page.getValue().getLength() <= 6000);
                players += page.getValue().getDescription().lines().filter(line -> line.startsWith("• ")).count();
                verify(edit).setActionRow(buttons.capture());
                navigation = List.copyOf(buttons.getValue());
            }
            assertEquals(500, players);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void navigatesWithoutDuplicateButtonIdsAndRestrictsNavigationToTheRequester() {
        var backend = mock(BackendAnalyticsService.class);
        var start = LocalDateTime.parse("2026-10-08T10:00:00");
        when(backend.getPlayerReport("Шкет", 7)).thenReturn(new PlayerActivityReport("Шкет", 7,
                start.minusDays(7), start.plusDays(1), "UTC", 60, 10,
                List.of(new DayActivity(start.toLocalDate(), 60,
                        List.of(new Session(start, start.plusMinutes(1), 60, false))))));
        var bot = new DiscordBot("NOT_SET", "RUST", backend);
        var command = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(command.getName()).thenReturn("rust_stats_week");
        when(command.getUser().getIdLong()).thenReturn(42L);
        when(command.getOption("nickname").getAsString()).thenReturn("Шкет");
        var reply = mock(WebhookMessageEditAction.class, RETURNS_SELF);
        when(command.getHook().editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(reply);

        bot.onSlashCommandInteraction(command);

        ArgumentCaptor<Collection<Button>> buttons = ArgumentCaptor.captor();
        verify(reply).setActionRow(buttons.capture());
        List<Button> overviewButtons = List.copyOf(buttons.getValue());
        assertEquals(3, overviewButtons.stream().map(Button::getId).distinct().count());
        assertTrue(overviewButtons.getFirst().isDisabled());
        assertFalse(overviewButtons.getLast().isDisabled());
        verify(command.deferReply()).complete();

        var click = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(click.getComponentId()).thenReturn(overviewButtons.getLast().getId());
        when(click.getUser().getIdLong()).thenReturn(99L);
        bot.onButtonInteraction(click);
        verify(click).reply(contains("Only the person"));
        verify(click, never()).editMessageEmbeds(any(MessageEmbed.class));

        var ownerClick = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(ownerClick.getComponentId()).thenReturn(overviewButtons.getLast().getId());
        when(ownerClick.getUser().getIdLong()).thenReturn(42L);
        var edit = mock(MessageEditCallbackAction.class, RETURNS_SELF);
        when(ownerClick.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(edit);
        bot.onButtonInteraction(ownerClick);
        verify(edit).setActionRow(buttons.capture());
        List<Button> timelineButtons = List.copyOf(buttons.getValue());
        assertEquals(3, timelineButtons.stream().map(Button::getId).distinct().count());
        assertTrue(timelineButtons.getLast().isDisabled());
        assertFalse(timelineButtons.get(1).isDisabled());
    }

    @Test
    void tellsTheUserToRefreshAReportAfterCacheLoss() {
        var bot = new DiscordBot("NOT_SET", "RUST", mock(BackendAnalyticsService.class));
        var click = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(click.getComponentId()).thenReturn("rust:report:missing:next:1");
        bot.onButtonInteraction(click);
        verify(click).reply(contains("expired"));
        verify(click, never()).editMessageEmbeds(any(MessageEmbed.class));
    }
}
