package com.poptsov.discordbotse.config;

import com.poptsov.discordbotse.service.BackendAnalyticsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContextConfiguration {

    // IP бекенд сервера аналитики
    @Value("${backend.api.url}")
    String baseUrl;
    // Ключ доступа к бекенд серверу аналитики
    @Value("${backend.api.key}")
    String backendApiKey;

    @Bean
    public BackendAnalyticsService backendAnalyticsServiceEngineers() {
        return new BackendAnalyticsService(baseUrl, backendApiKey, "/engineers");
    }

    @Bean
    public BackendAnalyticsService backendAnalyticsServiceRust() {
        return new BackendAnalyticsService(baseUrl, backendApiKey, "/rust");
    }

    @Bean
    public DiscordBot rustDiscordBot(@Value("${discord.bot.token.rust}") String botToken, @Qualifier("backendAnalyticsServiceRust") BackendAnalyticsService backendAnalyticsServiceRust) {
        return new DiscordBot(botToken, "RUST", backendAnalyticsServiceRust);
    }

    @Bean
    public DiscordBot engineersDiscordBot(@Value("${discord.bot.token.engineers}") String botToken, @Qualifier("backendAnalyticsServiceEngineers") BackendAnalyticsService backendAnalyticsServiceEngineers) {
        return new DiscordBot(botToken, "Space Engineers", backendAnalyticsServiceEngineers);
    }
}
