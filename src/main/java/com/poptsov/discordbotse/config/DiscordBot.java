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

    private final BackendAnalyticsService backendAnalyticsService;

    public DiscordBot(String botToken, String serverName, BackendAnalyticsService backendAnalyticsService) {
        this.botToken = botToken;
        this.serverName = serverName;
        this.backendAnalyticsService = backendAnalyticsService;
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
                Commands.slash("stats_week", "Получить аналитику игрока за неделю")
                        .addOption(OptionType.STRING, "nickname", "Никнейм игрока в Steam", true),
                Commands.slash("stats_month", "Получить аналитику игрока за месяц")
                        .addOption(OptionType.STRING, "nickname", "Никнейм игрока в Steam", true),
                Commands.slash("raw_data", "Получить сырые записи лога по игроку")
                        .addOption(OptionType.STRING, "nickname", "Никнейм игрока в Steam", true)
                        .addOption(OptionType.INTEGER, "days", "За сколько дней собрать логи (по умолчанию 3)", false),
                Commands.slash("all_nicknames", "Получить все никнеймы активных игроков за указанный промежуток времени")
                        .addOption(OptionType.INTEGER, "days", "За сколько дней собрать никнеймы (по умолчанию 7)", true)
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

            event.getHook().editOriginal(report).queue();

        } catch (Exception e) {
            event.getHook().editOriginal( "[" + serverName + "]: Произошла внутренняя ошибка при обработке запроса.").queue();
            e.printStackTrace();
        }
    }
}