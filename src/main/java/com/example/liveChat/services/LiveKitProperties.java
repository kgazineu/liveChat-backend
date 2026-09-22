package com.example.liveChat.services;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "livechat.livekit")
public class LiveKitProperties {
    private String apiUrl;
    private String clientUrl;
    private String apiKey;
    private String apiSecret;
    private Duration tokenTtl = Duration.ofMinutes(5);
    private Duration roomEmptyTimeout = Duration.ofMinutes(5);
    private boolean roomProvisioningEnabled = true;

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getClientUrl() {
        return clientUrl;
    }

    public void setClientUrl(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public Duration getRoomEmptyTimeout() {
        return roomEmptyTimeout;
    }

    public void setRoomEmptyTimeout(Duration roomEmptyTimeout) {
        this.roomEmptyTimeout = roomEmptyTimeout;
    }

    public boolean isRoomProvisioningEnabled() {
        return roomProvisioningEnabled;
    }

    public void setRoomProvisioningEnabled(boolean roomProvisioningEnabled) {
        this.roomProvisioningEnabled = roomProvisioningEnabled;
    }
}
