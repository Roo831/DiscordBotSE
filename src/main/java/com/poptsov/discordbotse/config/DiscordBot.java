package com.poptsov.discordbotse.config;

import com.poptsov.discordbotse.presentation.ReportFormatter;
import com.poptsov.discordbotse.service.BackendAnalyticsService;
import com.poptsov.discordbotse.service.DeliveryReceipts;
import com.poptsov.discordbotse.service.WatchlistDispatcher;
import com.poptsov.discordbotse.model.WatchNotification;
import com.poptsov.discordbotse.model.WatchRegistration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.scheduling.annotation.Scheduled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

public class DiscordBot extends ListenerAdapter {
    private static final Logger log = LogManager.getLogger(DiscordBot.class);
    private final String botToken;
    private final String serverName;
    private final String prefixSlashCommand;
    private final BackendAnalyticsService backendAnalyticsService;
    private final ReportFormatter formatter;
    private final WatchlistDispatcher watchlistDispatcher;
    private final Map<String, ReportPages> reports = new LinkedHashMap<>();
    private JDA jda;

    public DiscordBot(String botToken, String serverName, BackendAnalyticsService backendAnalyticsService) {
        this(botToken, serverName, backendAnalyticsService, Path.of("bot_logs"));
    }

    public DiscordBot(String botToken, String serverName, BackendAnalyticsService backendAnalyticsService, Path receiptsDirectory) {
        this.botToken = botToken;
        this.serverName = serverName;
        this.backendAnalyticsService = backendAnalyticsService;
        this.prefixSlashCommand = serverName.toLowerCase(Locale.ROOT);
        this.formatter = new ReportFormatter(serverName);
        this.watchlistDispatcher = new WatchlistDispatcher(backendAnalyticsService,
                new DeliveryReceipts(receiptsDirectory.resolve(prefixSlashCommand + "-receipts.properties")), this::sendNotification);
    }

    @PostConstruct
    public void startBot() throws Exception {
        if (botToken == null || botToken.isBlank() || botToken.equals("NOT_SET")) {
            log.info("[{}] Bot token is not configured; bot is disabled", serverName);
            return;
        }
        jda = JDABuilder.createDefault(botToken.trim()).addEventListeners(this)
                .setEventPool(Executors.newVirtualThreadPerTaskExecutor(), true).build();
        jda.awaitReady();
        jda.updateCommands().addCommands(
                Commands.slash(prefixSlashCommand + "_stats_week", "View a player's weekly activity")
                        .addOptions(nicknameOption()),
                Commands.slash(prefixSlashCommand + "_stats_month", "View a player's monthly activity")
                        .addOptions(nicknameOption()),
                Commands.slash(prefixSlashCommand + "_raw_data", "View a player's connection events")
                        .addOptions(nicknameOption(), daysOption("Number of days (default: 3)")),
                Commands.slash(prefixSlashCommand + "_all_nicknames", "View all active players")
                        .addOptions(daysOption("Number of days (default: 7)")),
                Commands.slash(prefixSlashCommand + "_watch", "🔔 Notify when the player next comes online")
                        .addOptions(nicknameOption()),
                Commands.slash(prefixSlashCommand + "_watchlist", "📋 My subscriptions in this channel"),
                Commands.slash(prefixSlashCommand + "_unwatch", "🔕 Unsubscribe from the player")
                        .addOption(OptionType.STRING, "subscription_id", "Subscription number from watchlist", true),
                Commands.slash(prefixSlashCommand + "_similar", "👥 Find players with similar activity")
                        .addOptions(nicknameOption(), daysOption("Period in days (default: 7)"))
        ).queue(null, error -> log.error("[{}] Command registration failed", serverName, error));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().startsWith(prefixSlashCommand + "_")) return;
        String command = event.getName().substring(prefixSlashCommand.length() + 1);
        boolean watchCommand = Set.of("watch", "watchlist", "unwatch").contains(command);
        try {
            if (watchCommand) event.deferReply(true).complete();
            else event.deferReply().complete();
        } catch (Exception error) {
            log.warn("[{}] Could not acknowledge command", serverName, error);
            return;
        }
        try {
            if (watchCommand && !event.isFromGuild()) {
                throw new IllegalArgumentException("Use tracking in the Discord server channel.");
            }
            WatchRegistration registration = command.equals("watch")
                    ? backendAnalyticsService.watch(nickname(event), event.getUser().getId(), event.getChannel().getId()) : null;
            List<MessageEmbed> pages = switch (command) {
                case "stats_week" -> formatter.playerActivity(backendAnalyticsService.getPlayerReport(nickname(event), 7));
                case "stats_month" -> formatter.playerActivity(backendAnalyticsService.getPlayerReport(nickname(event), 30));
                case "raw_data" -> formatter.rawData(backendAnalyticsService.getRawDataReport(nickname(event), days(event, 3)));
                case "all_nicknames" -> formatter.nicknames(backendAnalyticsService.getNicknames(days(event, 7)));
                case "similar" -> formatter.similarActivity(backendAnalyticsService.getSimilarActivity(nickname(event), days(event, 7)));
                case "watch" -> List.of(formatter.watchStarted(registration));
                case "watchlist" -> formatter.watchlist(backendAnalyticsService.getWatchlist(event.getUser().getId(), event.getChannel().getId()));
                case "unwatch" -> {
                    var option = event.getOption("subscription_id");
                    if (option == null || option.getAsString().isBlank()) throw new IllegalArgumentException("Specify the subscription number from the watchlist.");
                    backendAnalyticsService.unwatch(option.getAsString(), event.getUser().getId(), event.getChannel().getId());
                    yield List.of(formatter.watchRemoved());
                }
                default -> throw new IllegalArgumentException("This command is no longer available.");
            };
            String id = UUID.randomUUID().toString();
            if (pages.size() > 1) {
                synchronized (reports) {
                    reports.values().removeIf(report -> report.expiresAt().isBefore(Instant.now()));
                    if (reports.size() >= 100) reports.remove(reports.keySet().iterator().next());
                    reports.put(id, new ReportPages(event.getUser().getIdLong(), Instant.now().plusSeconds(900), pages));
                }
            }
            var reply = event.getHook().editOriginalEmbeds(pages.getFirst()).setAllowedMentions(Collections.emptyList());
            if (pages.size() > 1) reply.setActionRow(buttons(id, 0, pages.size()));
            else if (registration != null) reply.setActionRow(Button.danger(
                    prefixSlashCommand + ":unwatch:" + registration.subscription().id(), "🔕 Stop tracking"));
            reply.queue(null, error -> log.warn("[{}] Could not send report", serverName, error));
        } catch (IllegalArgumentException error) {
            event.getHook().editOriginalEmbeds(formatter.error(error.getMessage())).queue();
        } catch (RestClientResponseException error) {
            String message = "The analytics service is unavailable. Please try again later.";
            if (error.getStatusCode().value() == 400) {
                try {
                    var body = error.getResponseBodyAs(Map.class);
                    if (body != null && body.get("message") instanceof String reason) message = reason;
                } catch (RuntimeException ignored) { }
            }
            event.getHook().editOriginalEmbeds(formatter.error(message)).setAllowedMentions(Collections.emptyList()).queue();
        } catch (RestClientException error) {
            log.warn("[{}] Analytics request failed", serverName, error);
            event.getHook().editOriginalEmbeds(formatter.error("The analytics service is unavailable. Please try again later.")).queue();
        } catch (Exception error) {
            log.error("[{}] Report failed", serverName, error);
            event.getHook().editOriginalEmbeds(formatter.error("The report could not be prepared. Please try again later.")).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().startsWith(prefixSlashCommand + ":unwatch:")) {
            cancelWatch(event);
            return;
        }
        if (!event.getComponentId().startsWith(prefixSlashCommand + ":report:")) return;
        String[] parts = event.getComponentId().split(":");
        ReportPages report;
        synchronized (reports) {
            report = parts.length == 5 ? reports.get(parts[2]) : null;
        }
        if (report == null || report.expiresAt().isBefore(Instant.now())) {
            event.reply("This report has expired. Run the command again to refresh it.").setEphemeral(true).queue();
            return;
        }
        if (report.userId() != event.getUser().getIdLong()) {
            event.reply("Only the person who requested this report can change its page.").setEphemeral(true).queue();
            return;
        }
        try {
            int page = Integer.parseInt(parts[4]);
            if (page < 0 || page >= report.pages().size()) throw new IllegalArgumentException();
            event.editMessageEmbeds(report.pages().get(page))
                    .setActionRow(buttons(parts[2], page, report.pages().size())).queue();
        } catch (IllegalArgumentException error) {
            event.reply("This page is not available. Run the command again.").setEphemeral(true).queue();
        }
    }

    private void cancelWatch(ButtonInteractionEvent event) {
        try {
            event.deferEdit().complete();
            if (!event.isFromGuild()) throw new IllegalArgumentException("Use tracking in the Discord server channel.");
            String id = event.getComponentId().substring((prefixSlashCommand + ":unwatch:").length());
            backendAnalyticsService.unwatch(id, event.getUser().getId(), event.getChannel().getId());
            event.getHook().editOriginalEmbeds(formatter.watchRemoved()).setComponents(List.of()).queue();
        } catch (Exception error) {
            event.getHook().editOriginalEmbeds(formatter.error("Unable to cancel the subscription. Please try again.")).queue();
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void pollWatchlist() {
        if (jda != null && jda.getStatus() == JDA.Status.CONNECTED) watchlistDispatcher.poll();
    }

    private String sendNotification(WatchNotification event) {
        var channel = jda.getGuildChannelById(event.channelId());
        if (!(channel instanceof MessageChannel messages)) {
            throw new IllegalStateException("Notification channel is not accessible");
        }
        return messages.sendMessage("<@" + event.userId() + ">")
                .setEmbeds(formatter.online(event)).setAllowedMentions(Collections.emptyList())
                .mentionUsers(event.userId()).complete().getId();
    }

    private List<Button> buttons(String id, int page, int count) {
        String prefix = prefixSlashCommand + ":report:" + id + ":";
        return List.of(Button.secondary(prefix + "back:" + Math.max(0, page - 1), "◀ Back").withDisabled(page == 0),
                Button.primary(prefix + "overview:0", "Overview").withDisabled(page == 0),
                Button.secondary(prefix + "next:" + Math.min(count - 1, page + 1), "Next ▶").withDisabled(page == count - 1));
    }

    private static String nickname(SlashCommandInteractionEvent event) {
        var option = event.getOption("nickname");
        if (option == null || option.getAsString().isBlank()) throw new IllegalArgumentException("Please enter a player nickname.");
        return option.getAsString();
    }

    private static int days(SlashCommandInteractionEvent event, int fallback) {
        var option = event.getOption("days");
        int days = option == null ? fallback : option.getAsInt();
        if (days < 1 || days > 365) throw new IllegalArgumentException("Choose a period between 1 and 365 days.");
        return days;
    }

    private static OptionData nicknameOption() {
        return new OptionData(OptionType.STRING, "nickname", "Player's Steam nickname", true).setMinLength(1).setMaxLength(128);
    }

    private static OptionData daysOption(String description) {
        return new OptionData(OptionType.INTEGER, "days", description).setRequiredRange(1, 365);
    }

    @PreDestroy
    public void stopBot() {
        if (jda != null) jda.shutdown();
    }

    private record ReportPages(long userId, Instant expiresAt, List<MessageEmbed> pages) {}
}
