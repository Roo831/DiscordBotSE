package com.poptsov.discordbotse.presentation;

import com.poptsov.discordbotse.model.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ReportFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss", Locale.ENGLISH);
    private final String game;
    private final String icon;
    private final int color;

    public ReportFormatter(String game) {
        boolean rust = game.equalsIgnoreCase("RUST");
        this.game = rust ? "Rust" : "Space Engineers";
        this.icon = rust ? "☢️" : "🚀";
        this.color = rust ? 0xE67E22 : 0x5865F2;
    }

    public List<MessageEmbed> playerActivity(PlayerActivityReport report) {
        String description = "**" + safe(report.nickname()) + "** · Last " + report.days() + " days\n"
                + period(report.periodStart(), report.periodEnd());
        List<EmbedBuilder> pages = new ArrayList<>();
        EmbedBuilder summary = base("Player activity", description)
                .addField("⏱️ Online time", duration(report.totalSeconds()), true)
                .addField("📅 Active days", String.valueOf(report.dailyActivity().size()), true)
                .addField("🕒 Peak hour", report.peakHour() == null ? "—" : String.format(Locale.ROOT,
                        "%02d:00 – %02d:00", report.peakHour(), (report.peakHour() + 1) % 24), true);
        if (report.dailyActivity().isEmpty()) {
            summary.addField("📭 No activity", "No recorded sessions in this period.", false);
        } else {
            long maximum = report.dailyActivity().stream().mapToLong(day -> day.totalSeconds()).max().orElse(0);
            StringBuilder recent = new StringBuilder("```text\n");
            int first = Math.max(0, report.dailyActivity().size() - 7);
            for (var day : report.dailyActivity().subList(first, report.dailyActivity().size())) {
                int bars = maximum == 0 ? 0 : Math.max(1, (int)Math.round(day.totalSeconds() * 10.0 / maximum));
                recent.append(day.date().format(DATE)).append("  ").append("▰".repeat(bars))
                        .append("▱".repeat(10 - bars)).append("  ").append(duration(day.totalSeconds())).append('\n');
            }
            summary.addField("📊 Recent activity", recent.append("```").toString(), false);
            summary.appendDescription("\n\nUse **Next** to view the complete session timeline.");
        }
        pages.add(summary);
        EmbedBuilder timeline = null;
        int fields = 0;
        for (var day : report.dailyActivity()) {
            for (int first = 0; first < day.sessions().size(); first += 8) {
                if (timeline == null || fields == 3) {
                    timeline = base("Session timeline", description);
                    pages.add(timeline);
                    fields = 0;
                }
                StringBuilder intervals = new StringBuilder("```text\n");
                for (var session : day.sessions().subList(first, Math.min(first + 8, day.sessions().size()))) {
                    intervals.append(session.start().format(TIME)).append(" → ").append(session.end().format(TIME))
                            .append("  ").append(duration(session.durationSeconds()))
                            .append(session.ongoing() ? " · ongoing" : "").append('\n');
                }
                timeline.addField(day.date().format(DATE) + " · " + duration(day.totalSeconds())
                        + (first == 0 ? "" : " · continued"), intervals.append("```").toString(), false);
                fields++;
            }
        }
        return finish(pages, report.timeZone());
    }

    public List<MessageEmbed> rawData(RawDataReport report) {
        String description = "**" + safe(report.nickname()) + "** · " + report.events().size() + " events\n"
                + period(report.periodStart(), report.periodEnd());
        List<String> lines = report.events().stream().map(event ->
                (event.action().equals("CONNECT") ? "🟢" : "🔴") + " **" + event.action() + "** · "
                        + event.timestamp().format(EVENT_TIME) + " · " + safe(event.nickname())).toList();
        return textPages("Activity events", description, lines, "No recorded events in this period.", report.timeZone());
    }

    public List<MessageEmbed> nicknames(NicknameReport report) {
        String description = "**" + report.nicknames().size() + " players** · Last " + report.days() + " days\n"
                + period(report.periodStart(), report.periodEnd());
        return textPages("Active players", description, report.nicknames().stream().map(name -> "• " + safe(name)).toList(),
                "No active players found in this period.", report.timeZone());
    }

    public MessageEmbed error(String message) {
        return base("Could not load report", message).setColor(0xED4245).build();
    }

    private List<MessageEmbed> textPages(String title, String description, List<String> lines, String empty, String zone) {
        List<EmbedBuilder> pages = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String line : lines) {
            while (!line.isEmpty()) {
                int length = Math.min(line.length(), 2800);
                if (length < line.length() && Character.isHighSurrogate(line.charAt(length - 1))) length--;
                String part = line.substring(0, length);
                if (chunk.length() + part.length() + 1 > 3000) {
                    pages.add(base(title, description + "\n\n" + chunk));
                    chunk.setLength(0);
                }
                chunk.append(part).append('\n');
                line = line.substring(length);
            }
        }
        if (!chunk.isEmpty()) pages.add(base(title, description + "\n\n" + chunk));
        if (pages.isEmpty()) pages.add(base(title, description + "\n\n📭 " + empty));
        return finish(pages, zone);
    }

    private EmbedBuilder base(String title, String description) {
        return new EmbedBuilder().setColor(color).setTitle(icon + " " + game + " · " + title).setDescription(description);
    }

    private List<MessageEmbed> finish(List<EmbedBuilder> pages, String zone) {
        List<MessageEmbed> result = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            result.add(pages.get(i).setFooter("Times: " + zone + " • Page " + (i + 1) + "/" + pages.size()).build());
        }
        return List.copyOf(result);
    }

    private static String safe(String value) {
        return MarkdownSanitizer.escape(value.replace('\n', ' ').replace('\r', ' '));
    }

    private static String period(LocalDateTime start, LocalDateTime end) {
        return start.format(DATE) + " – " + end.format(DATE) + " " + end.getYear();
    }

    private static String duration(long seconds) {
        if (seconds > 0 && seconds < 60) return "<1m";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        return minutes / 60 + "h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + "m");
    }
}