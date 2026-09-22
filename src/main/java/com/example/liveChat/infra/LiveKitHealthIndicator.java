package com.example.liveChat.infra;

import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import java.util.List;

@Component("liveKit")
@Profile("!test")
public class LiveKitHealthIndicator extends AbstractHealthIndicator {
    private final RoomServiceClient roomServiceClient;

    public LiveKitHealthIndicator(RoomServiceClient roomServiceClient) {
        this.roomServiceClient = roomServiceClient;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        Response<List<LivekitModels.Room>> response = roomServiceClient.listRooms().execute();
        if (response.isSuccessful()) {
            builder.up();
            return;
        }
        builder.down().withDetail("status", response.code());
    }
}
