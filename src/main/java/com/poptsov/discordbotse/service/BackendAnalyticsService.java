package com.poptsov.discordbotse.service;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class BackendAnalyticsService {

    private final RestClient restClient;

    public BackendAnalyticsService(String baseUrl, String apiKey, String httpDomain) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + httpDomain)
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
            return "Error connecting to the analytics server. Please try again later.";
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
            return "Error connecting to the analytics server. Please try again later.";
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
            return "Error connecting to the analytics server. Please try again later.";
        }
    }
}