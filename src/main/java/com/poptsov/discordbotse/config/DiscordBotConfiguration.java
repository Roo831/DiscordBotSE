package com.poptsov.discordbotse.config;


import com.poptsov.discordbotse.service.BackendAnalyticsService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
public class DiscordBotConfiguration extends ListenerAdapter {

    @Value("${discord.bot.token}")
    private String botToken;

    private final BackendAnalyticsService backendAnalyticsService;

    public DiscordBotConfiguration(BackendAnalyticsService backendAnalyticsService) {
        this.backendAnalyticsService = backendAnalyticsService;
    }

    @PostConstruct
    public void startBot() throws Exception {
        if (botToken == null || botToken.trim().isEmpty() || botToken.equals("NOT_SET")) {
            System.err.println("[DISCORD ERROR] Токен бота не настроен в конфигурации!");
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
                Commands.slash("nicknames", "Получить никнеймы активных игроков за указанный промежуток времени")
                        .addOption(OptionType.INTEGER, "days", "За сколько дней собрать никнеймы (по умолчанию 7)", false)
        ).queue();

        System.out.println("[DISCORD INFO] Бот успешно запущен и зарегистрировал слэш-команды!");
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
            } else if (event.getName().equals("nicknames")) {
                OptionMapping daysOption = event.getOption("days");
                int days = (daysOption != null) ? daysOption.getAsInt() : 7;
                report = backendAnalyticsService.getNicknames(days);
            } else {
                return;
            }

            event.getHook().editOriginal(report).queue();

        } catch (Exception e) {
            event.getHook().editOriginal("Произошла внутренняя ошибка при обработке запроса.").queue();
            e.printStackTrace();
        }
    }
}