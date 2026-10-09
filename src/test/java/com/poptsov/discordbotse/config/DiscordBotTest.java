package com.poptsov.discordbotse.config;

import com.poptsov.discordbotse.model.PlayerActivityReport;
import com.poptsov.discordbotse.model.PlayerActivityReport.DayActivity;
import com.poptsov.discordbotse.model.PlayerActivityReport.Session;
import com.poptsov.discordbotse.service.BackendAnalyticsService;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscordBotTest {
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
