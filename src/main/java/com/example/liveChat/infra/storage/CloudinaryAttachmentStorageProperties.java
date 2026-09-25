package com.example.liveChat.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test")
@ConfigurationProperties(prefix = "livechat.attachments.cloudinary")
public class CloudinaryAttachmentStorageProperties {
    private String cloudName;
    private String apiKey;
    private String apiSecret;
    private String uploadPreset;
    private Duration downloadUrlTtl = Duration.ofMinutes(5);

    public String getCloudName() {
        return cloudName;
    }

    public void setCloudName(String cloudName) {
        this.cloudName = cloudName;
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

    public String getUploadPreset() {
        return uploadPreset;
    }

    public void setUploadPreset(String uploadPreset) {
        this.uploadPreset = uploadPreset;
    }

    public Duration getDownloadUrlTtl() {
        return downloadUrlTtl;
    }

    public void setDownloadUrlTtl(Duration downloadUrlTtl) {
        this.downloadUrlTtl = downloadUrlTtl;
    }
}
