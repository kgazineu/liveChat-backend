package com.example.liveChat.services;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "livechat.media")
public class MediaPresenceProperties {
    private Duration reconnectionTimeout = Duration.ofSeconds(30);
    private int maxServerParticipants = 5;

    public Duration getReconnectionTimeout() {
        return reconnectionTimeout;
    }

    public void setReconnectionTimeout(Duration reconnectionTimeout) {
        this.reconnectionTimeout = reconnectionTimeout;
    }

    public int getMaxServerParticipants() {
        return maxServerParticipants;
    }

    public void setMaxServerParticipants(int maxServerParticipants) {
        this.maxServerParticipants = maxServerParticipants;
    }
}
