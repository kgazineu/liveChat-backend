package com.example.liveChat.infra;

import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveKitHealthIndicatorTests {
    @Mock private RoomServiceClient roomServiceClient;
    @Mock private Call<List<LivekitModels.Room>> listRoomsCall;

    private LiveKitHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new LiveKitHealthIndicator(roomServiceClient);
        when(roomServiceClient.listRooms()).thenReturn(listRoomsCall);
    }

    @Test
    void reportsUpWhenLiveKitAcceptsAnAuthenticatedRequest() throws Exception {
        when(listRoomsCall.execute()).thenReturn(Response.success(List.of()));

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenLiveKitRejectsTheRequest() throws Exception {
        ResponseBody body = ResponseBody.create("", MediaType.get("text/plain"));
        when(listRoomsCall.execute()).thenReturn(Response.error(401, body));

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenLiveKitCannotBeReached() throws Exception {
        when(listRoomsCall.execute()).thenThrow(new IOException("offline"));

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
