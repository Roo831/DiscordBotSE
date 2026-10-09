package com.poptsov.discordbotse.service;

import com.poptsov.discordbotse.model.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class BackendAnalyticsService {
    private final RestClient restClient;

    public BackendAnalyticsService(String baseUrl, String apiKey, String httpDomain) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().baseUrl(baseUrl + httpDomain)
                .requestFactory(factory).defaultHeader("X-API-KEY", apiKey).build();
    }

    public NicknameReport getNicknames(int days) {
        return restClient.get().uri(builder -> builder.path("/nicknames").queryParam("days", days).build())
                .accept(MediaType.APPLICATION_JSON).retrieve().body(NicknameReport.class);
    }

    public PlayerActivityReport getPlayerReport(String nickname, int days) {
        return restClient.get().uri(builder -> builder.path("/stats")
                        .queryParam("nickname", "{nickname}").queryParam("days", days).build(nickname))
                .accept(MediaType.APPLICATION_JSON).retrieve().body(PlayerActivityReport.class);
    }

    public RawDataReport getRawDataReport(String nickname, int days) {
        return restClient.get().uri(builder -> builder.path("/raw")
                        .queryParam("nickname", "{nickname}").queryParam("days", days).build(nickname))
                .accept(MediaType.APPLICATION_JSON).retrieve().body(RawDataReport.class);
    }
}
