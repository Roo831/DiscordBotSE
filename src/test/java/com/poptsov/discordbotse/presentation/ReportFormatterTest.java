package com.poptsov.discordbotse.presentation;

import com.poptsov.discordbotse.model.PlayerActivityReport;
import com.poptsov.discordbotse.model.PlayerActivityReport.DayActivity;
import com.poptsov.discordbotse.model.PlayerActivityReport.Session;
import com.poptsov.discordbotse.model.RawDataReport;
import com.poptsov.discordbotse.model.NicknameReport;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportFormatterTest {
    private PlayerActivityReport report(List<Session> sessions) {
        var start = LocalDateTime.parse("2026-10-08T10:00:00");
        return new PlayerActivityReport("Шкет", 7, start.minusDays(7), start.plusDays(1), "UTC",
                sessions.size() * 60L, 10, List.of(new DayActivity(start.toLocalDate(), sessions.size() * 60L, sessions)));
    }

    @Test
    void rendersACompactColouredOverviewBeforeTheTimeline() {
        var start = LocalDateTime.parse("2026-10-08T10:00:00");
        var pages = new ReportFormatter("RUST").playerActivity(report(List.of(new Session(start, start.plusMinutes(1), 60, false))));
        assertEquals(2, pages.size());
        MessageEmbed overview = pages.get(0);
        assertNotEquals(0, overview.getColorRaw());
        assertTrue(overview.getDescription().contains("Шкет"));
        assertTrue(overview.getFields().stream().anyMatch(field -> field.getValue().equals("1m")));
        assertTrue(pages.get(1).getFields().get(0).getValue().contains("10:00 → 10:01"));
    }

    @Test
    void keepsEverySessionAndStaysWithinDiscordLimitsForLongReports() {
        var start = LocalDateTime.parse("2026-10-08T10:00:00");
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            sessions.add(new Session(start.plusMinutes(i), start.plusMinutes(i + 1), 60, false));
        }
        var pages = new ReportFormatter("Space_Engineers").playerActivity(report(sessions));
        assertTrue(pages.size() > 2);
        long renderedIntervals = pages.stream().flatMap(page -> page.getFields().stream())
                .flatMap(field -> field.getValue().lines()).filter(line -> line.contains(" → ")).count();
        assertEquals(180, renderedIntervals);
        for (MessageEmbed page : pages) {
            assertTrue(page.getLength() <= 6000);
            assertTrue(page.getTitle().length() <= 256);
            assertTrue(page.getFields().size() <= 25);
            for (var field : page.getFields()) assertTrue(field.getValue().length() <= 1024);
        }
    }

    @Test
    void paginatesRawEventsAndNamesWithoutDroppingEntriesOrBreakingUnicode() {
        var start = LocalDateTime.parse("2026-10-08T10:00:00");
        var formatter = new ReportFormatter("RUST");
        List<RawDataReport.ActivityEvent> events = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String name = "Player " + i + " 🚀 _name_ @everyone";
            names.add(name);
            events.add(new RawDataReport.ActivityEvent(start.plusSeconds(i), i % 2 == 0 ? "CONNECT" : "DISCONNECT", name));
        }
        var raw = formatter.rawData(new RawDataReport("Шкет", 7, start.minusDays(7), start, "UTC", events));
        var players = formatter.nicknames(new NicknameReport(7, start.minusDays(7), start, "UTC", names));
        assertTrue(raw.size() > 1);
        assertTrue(players.size() > 1);
        assertEquals(100, raw.stream().flatMap(page -> page.getDescription().lines()).filter(line -> line.contains("**CONNECT**") || line.contains("**DISCONNECT**")).count());
        assertEquals(100, players.stream().flatMap(page -> page.getDescription().lines()).filter(line -> line.startsWith("• ")).count());
        for (MessageEmbed page : raw) {
            assertTrue(page.getLength() <= 6000);
            assertTrue(page.getDescription().length() <= 4096);
            assertTrue(page.getDescription().contains("\\_name\\_"));
        }
        var oversizedName = "🚀".repeat(2000);
        var longNames = formatter.nicknames(new NicknameReport(7, start.minusDays(7), start, "UTC", List.of(oversizedName)));
        assertEquals(2000, longNames.stream().mapToLong(page -> page.getDescription().codePoints().filter(point -> point == 0x1F680).count()).sum());
        assertTrue(longNames.stream().allMatch(page -> page.getDescription().length() <= 4096));
    }

    @Test
    void showsAnEmptyStateAndUsesDifferentColoursForTheTwoGames() {
        var start = LocalDateTime.parse("2026-10-08T10:00:00");
        var report = new PlayerActivityReport("Шкет", 7, start.minusDays(7), start, "UTC", 0, null, List.of());
        var rust = new ReportFormatter("RUST").playerActivity(report);
        var engineers = new ReportFormatter("Space_Engineers").playerActivity(report);
        assertEquals(1, rust.size());
        assertTrue(rust.getFirst().getFields().stream().anyMatch(field -> field.getName().contains("No activity")));
        assertNotEquals(rust.getFirst().getColorRaw(), engineers.getFirst().getColorRaw());
        assertTrue(rust.getFirst().getFooter().getText().contains("UTC"));
        assertEquals(0xED4245, new ReportFormatter("RUST").error("Unavailable").getColorRaw());
    }
}
