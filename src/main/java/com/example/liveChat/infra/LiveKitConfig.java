package com.example.liveChat.infra;

import com.example.liveChat.services.LiveKitProperties;
import io.livekit.server.RoomServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveKitConfig {
    @Bean
    public RoomServiceClient roomServiceClient(LiveKitProperties properties) {
        return RoomServiceClient.createClient(properties.getApiUrl(), properties.getApiKey(), properties.getApiSecret());
    }
}
