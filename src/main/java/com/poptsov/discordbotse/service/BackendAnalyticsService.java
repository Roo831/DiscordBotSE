package com.poptsov.discordbotse.service;

import com.poptsov.discordbotse.model.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.time.Instant;

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

    public SimilarActivityReport getSimilarActivity(String nickname, int days) {
        return restClient.get().uri(builder -> builder.path("/similar")
                        .queryParam("nickname", "{nickname}").queryParam("days", days).build(nickname))
                .accept(MediaType.APPLICATION_JSON).retrieve().body(SimilarActivityReport.class);
    }

    public WatchRegistration watch(String nickname, String userId, String channelId) {
        return restClient.post().uri(builder -> builder.path("/watchlist")
                        .queryParam("nickname", "{nickname}").queryParam("userId", "{userId}")
                        .queryParam("channelId", "{channelId}").build(nickname, userId, channelId))
                .accept(MediaType.APPLICATION_JSON).retrieve().body(WatchRegistration.class);
    }

    public List<WatchSubscription> getWatchlist(String userId, String channelId) {
        return restClient.get().uri(builder -> builder.path("/watchlist")
                        .queryParam("userId", "{userId}").queryParam("channelId", "{channelId}")
                        .build(userId, channelId))
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .body(new ParameterizedTypeReference<List<WatchSubscription>>() {});
    }

    public void unwatch(String subscriptionId, String userId, String channelId) {
        restClient.delete().uri(builder -> builder.path("/watchlist/{id}")
                        .queryParam("userId", "{userId}").queryParam("channelId", "{channelId}")
                        .build(subscriptionId, userId, channelId)).retrieve().toBodilessEntity();
    }

    public List<WatchNotification> getPendingNotifications() {
        return restClient.get().uri("/watchlist/pending").accept(MediaType.APPLICATION_JSON).retrieve()
                .body(new ParameterizedTypeReference<List<WatchNotification>>() {});
    }

    public List<WatchNotification> getPendingNotifications(Instant afterObservedAt, String afterEventId) {
        return restClient.get().uri(builder -> builder.path("/watchlist/pending")
                        .queryParam("afterObservedAt", "{time}").queryParam("afterEventId", "{event}")
                        .build(afterObservedAt.toString(), afterEventId))
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .body(new ParameterizedTypeReference<List<WatchNotification>>() {});
    }

    public void acknowledgeNotification(String eventId) {
        restClient.post().uri("/watchlist/pending/{id}/ack", eventId).retrieve().toBodilessEntity();
    }
}
