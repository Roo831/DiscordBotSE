package com.poptsov.discordbotse.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class BackendAnalyticsService {

    private final RestClient restClient;

    public BackendAnalyticsService(
            @Value("${backend.api.url}") String baseUrl,
            @Value("${backend.api.key}") String apiKey) {

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", apiKey)
                .build();
    }

    public String getNicknames(int days) {
        try {
           return restClient.get().uri(uriBuilder -> uriBuilder.path("/nicknames")
                            .queryParam("days", days)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            return "Ошибка соединения с сервером аналитики. Попробуйте позже.";
        }
    }

    public String getPlayerReport(String nickname, int days) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stats")
                            .queryParam("nickname", nickname)
                            .queryParam("days", days)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            return "Ошибка соединения с сервером аналитики. Попробуйте позже.";
        }
    }

    public String getRawDataReport(String nickname, int days) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/raw")
                            .queryParam("nickname", nickname)
                            .queryParam("days", days)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            return "Ошибка соединения с сервером аналитики. Попробуйте позже.";
        }
    }
}