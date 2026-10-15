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
        return base("Could not load report", safe(message)).setColor(0xED4245).build();
    }

    public MessageEmbed watchStarted(WatchRegistration registration) {
        var watch = registration.subscription();
        String state = registration.alreadyOnline()
                ? "🟢 The player is already online. A notification is pending delivery to this channel."
                : watch.status().equals("PENDING") ? "📨 Login detected. Notification pending dispatch."
                : "🔔 I’ll let you know in this channel when the player comes online.";
        return base("Tracking enabled", "**" + safe(watch.nickname()) + "**\n\n" + state
                + "\n\nI will notify you **once**. The subscription will persist after a restart.")
                .setColor(registration.alreadyOnline() ? 0x57F287 : 0xFEE75C)
                .addField("🧾 Subscription number", "`" + watch.id() + "`", false)
                .setFooter("By nickname • Changing your nickname may hinder discovery").build();
    }

    public List<MessageEmbed> watchlist(List<WatchSubscription> watches) {
        List<String> lines = watches.stream().map(watch ->
                (watch.status().equals("PENDING") ? "📨" : "🟡") + " **" + safe(watch.nickname()) + "**\n"
                        + (watch.status().equals("PENDING") ? "Notification pending dispatch" : "Awaiting input")
                        + " · ID: `" + watch.id() + "`\n").toList();
        var pages = textPages("My subscriptions", "**" + watches.size() + "** in this channel · Limit of **25** per game\n"
                        + "To cancel, use the **unwatch** command with the subscription number.",
                lines, "There are no subscriptions in this channel yet.", "UTC");
        List<MessageEmbed> result = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            result.add(new EmbedBuilder(pages.get(i)).setFooter("One-time subscriptions • Page "
                    + (i + 1) + "/" + pages.size()).build());
        }
        return List.copyOf(result);
    }

    public MessageEmbed watchRemoved() {
        return base("Tracking disabled", "🔕 Subscription cancelled. A message that is already being sent may still arrive.")
                .setColor(0x95A5A6).build();
    }

    public MessageEmbed online(WatchNotification event) {
        return base("Player online", "🟢 **" + safe(event.nickname()) + "** spotted on the server.\n\n"
                        + "🕒 Detected <t:" + event.observedAt().getEpochSecond() + ":R>\n"
                        + "📍 Server **" + game + "**")
                .setColor(0x57F287).setTimestamp(event.observedAt())
                .setFooter("One-time notification • By nickname • Detection time, not exact login time").build();
    }

    public List<MessageEmbed> similarActivity(SimilarActivityReport report) {
        boolean hasSessionEvidence = report.players().stream()
                .allMatch(player -> player.sharedSessions() != null && player.rankingScore() != null);
        String description = "**" + safe(report.nickname()) + "** · " + report.days() + " days\n"
                + period(report.periodStart(), report.periodEnd()) + "\n\n"
                + "Temporal overlap **does not confirm** a friendship or a shared team.";
        List<EmbedBuilder> pages = new ArrayList<>();
        pages.add(base("Similar activity", description).setColor(0x9B59B6)
                .addField("⏱️ Player activity", duration(report.totalSeconds()), true)
                .addField("👥 Candidates", String.valueOf(report.players().size()), true)
                .addField("📊 How candidates are ranked", hasSessionEvidence
                        ? "Score = schedule overlap × shared recorded sessions. "
                        + "A shared session requires at least 15 minutes together during one of the target's sessions."
                        : "Players are ordered by schedule overlap. Shared-session counts are unavailable for this report.", false)
                .addField("📐 Schedule overlap", "Shared time ÷ time when at least one player was online. "
                        + "This percentage is not a probability of being teammates.", false));
        if (report.players().isEmpty()) {
            pages.getFirst().addField("📭 No candidates", "No shared sessions of at least 15 minutes were recorded in this period.", false);
        }
        for (int first = 0; first < report.players().size(); first += 4) {
            var page = base("Similar activity · rating", description).setColor(0x9B59B6);
            for (int i = first; i < Math.min(first + 4, report.players().size()); i++) {
                var player = report.players().get(i);
                int bars = Math.max(0, Math.min(10, (int) Math.round(player.similarityPercent() / 10)));
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "▫️";
                String name = medal + " " + (i + 1) + ". " + safe(player.nickname());
                if (name.length() > 240) name = name.substring(0, Character.isHighSurrogate(name.charAt(238)) ? 238 : 239) + "…";
                page.addField(name, "`" + "▰".repeat(bars) + "▱".repeat(10 - bars) + "` **"
                        + percent(player.similarityPercent()) + "** schedule overlap\n"
                        + "⏱️ Spent Together **" + duration(player.overlapSeconds()) + "** · 📅 Total days: **" + player.sharedDays() + "**\n"
                        + (player.sharedSessions() == null || player.rankingScore() == null ? ""
                        : "🤝 Shared sessions **" + player.sharedSessions() + "** · 🏅 Score **"
                        + String.format(Locale.ROOT, "%.1f", player.rankingScore()) + "**\n")
                        + "Jointly: **" + percent(player.targetOverlapPercent()) + "** of the target time / **"
                        + percent(player.playerOverlapPercent()) + "** of this player's time", false);
            }
            pages.add(page);
        }
        return finish(pages, report.timeZone());
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
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
