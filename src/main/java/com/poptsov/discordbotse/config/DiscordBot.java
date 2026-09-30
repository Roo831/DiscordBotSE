package com.poptsov.discordbotse.config;

import com.poptsov.discordbotse.service.BackendAnalyticsService;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;


public class DiscordBot extends ListenerAdapter {

    private String botToken;

    private String serverName;

    private String prefixSlashCommand;

    private final BackendAnalyticsService backendAnalyticsService;

    public DiscordBot(String botToken, String serverName, BackendAnalyticsService backendAnalyticsService) {
        this.botToken = botToken;
        this.serverName = serverName;
        this.backendAnalyticsService = backendAnalyticsService;
        this.prefixSlashCommand = serverName.toLowerCase();
    }

    @PostConstruct
    public void startBot() throws Exception {
        if (botToken == null || botToken.trim().isEmpty() || botToken.equals("NOT_SET")) {
            System.err.println("[" + serverName + "]: Токен бота не настроен в конфигурации!");
            return;
        }

        JDA jda = JDABuilder.createDefault(botToken.trim())
                .addEventListeners(this)
                .build();

        jda.awaitReady();
        jda.updateCommands().addCommands(
                Commands.slash(prefixSlashCommand + "_stats_week", "Get player analytics for the week")
                        .addOption(OptionType.STRING, "nickname", "Player's Steam nickname", true),
                Commands.slash(prefixSlashCommand  +"_stats_month", "Get player analytics for the month")
                        .addOption(OptionType.STRING, "nickname", "Player's Steam nickname", true),
                Commands.slash(prefixSlashCommand + "_raw_data", "Retrieve raw log entries for a player")
                        .addOption(OptionType.STRING, "nickname", "Player's Steam nickname", true)
                        .addOption(OptionType.INTEGER, "days", "Number of days of logs to collect (default: 3)", false),
                Commands.slash(prefixSlashCommand + "_all_nicknames", "Retrieve the nicknames of all active players for the specified time period.")
                        .addOption(OptionType.INTEGER, "days", "How many days to collect nicknames (default: 7)", true)
        ).queue();

        System.out.println("[" + serverName + "]: Бот успешно запущен и зарегистрировал слэш-команды!");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String report;

        try {
            if (event.getName().equals("stats_week")) {
                String nickname = event.getOption("nickname").getAsString();
                report = backendAnalyticsService.getPlayerReport(nickname, 7);
            } else if (event.getName().equals("stats_month")) {
                String nickname = event.getOption("nickname").getAsString();
                report = backendAnalyticsService.getPlayerReport(nickname, 30);
            } else if (event.getName().equals("raw_data")) {
                String nickname = event.getOption("nickname").getAsString();
                OptionMapping daysOption = event.getOption("days");
                int days = (daysOption != null) ? daysOption.getAsInt() : 3;
                report = backendAnalyticsService.getRawDataReport(nickname, days);
            } else if (event.getName().equals("all_nicknames")) {
                OptionMapping daysOption = event.getOption("days");
                int days = (daysOption != null) ? daysOption.getAsInt() : 7;
                report = backendAnalyticsService.getNicknames(days);
            } else {
                return;
            }

            int maxLength = 1900;
            if (report == null || report.trim().isEmpty()) {
                event.getHook().editOriginal("Data not found").queue();
            } else if (report.length() <= maxLength) {
                event.getHook().editOriginal(report).queue();
            } else {
                event.getHook().editOriginal(report.substring(0, maxLength) + "\n*(continued below...)*").queue();

                for (int i = maxLength; i < report.length(); i += maxLength) {
                    int endIndex = Math.min(i + maxLength, report.length());
                    String chunk = report.substring(i, endIndex);

                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                    event.getHook().sendMessage("```text\n" + chunk + "\n```").queue();
                }
            }

        } catch (Exception e) {
            System.err.println("[" + serverName + "]: Ошибка при обработке команды: " + e.getMessage());
            e.printStackTrace();
            event.getHook().editOriginal("Произошла внутренняя ошибка при обработке запроса.").queue();
        }
    }
}