package com.example.liveChat;

import com.example.liveChat.dto.LiveKitConnectionDTO;
import com.example.liveChat.exceptions.MediaInfrastructureUnavailableException;
import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;
import com.example.liveChat.models.User;
import com.example.liveChat.services.LiveKitMediaService;
import com.example.liveChat.services.LiveKitProperties;
import com.example.liveChat.services.MediaPresenceProperties;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveKitMediaServiceTests {
    @Mock private RoomServiceClient roomServiceClient;
    @Mock private Call<List<LivekitModels.Room>> listRoomsCall;
    @Mock private Call<LivekitModels.Room> createRoomCall;
    @Mock private Call<Void> removeParticipantCall;

    private LiveKitMediaService service;

    @BeforeEach
    void setUp() {
        LiveKitProperties properties = new LiveKitProperties();
        properties.setApiUrl("http://livekit:7880");
        properties.setClientUrl("wss://media.example.test");
        properties.setApiKey("test-key");
        properties.setApiSecret("test-secret-at-least-32-characters-long");
        properties.setTokenTtl(Duration.ofMinutes(5));
        properties.setRoomEmptyTimeout(Duration.ofMinutes(5));

        MediaPresenceProperties presenceProperties = new MediaPresenceProperties();
        presenceProperties.setMaxServerParticipants(5);
        service = new LiveKitMediaService(properties, presenceProperties, roomServiceClient);
    }

    @Test
    void createsServerVoiceRoomWithTheBackendLimit() throws Exception {
        String channelId = "2f333a44-2f44-4f56-a555-9d5f16933d80";
        String roomName = "server-voice-" + channelId;
        when(roomServiceClient.listRooms(List.of(roomName))).thenReturn(listRoomsCall);
        when(listRoomsCall.execute()).thenReturn(Response.success(List.of()));
        when(roomServiceClient.createRoom(roomName, 300, 5)).thenReturn(createRoomCall);
        when(createRoomCall.execute()).thenReturn(Response.success(room(roomName)));

        LiveKitConnectionDTO connection = service.prepareConnection(MediaChannelKind.SERVER_VOICE, channelId, user());

        assertEquals(roomName, connection.roomName());
        assertEquals("wss://media.example.test", connection.url());
        verify(roomServiceClient).createRoom(roomName, 300, 5);
    }

    @Test
    void reusesAnExistingDirectRoomWithoutCreatingAnotherOne() throws Exception {
        String channelId = "a7498b14-e671-43f0-9906-c03f6fcefd20";
        String roomName = "direct-" + channelId;
        when(roomServiceClient.listRooms(List.of(roomName))).thenReturn(listRoomsCall);
        when(listRoomsCall.execute()).thenReturn(Response.success(List.of(room(roomName))));

        LiveKitConnectionDTO connection = service.prepareConnection(MediaChannelKind.DIRECT, channelId, user());

        assertEquals(roomName, connection.roomName());
        verify(roomServiceClient, never()).createRoom(roomName, 300, 2);
    }

    @Test
    void disconnectsTheParticipantFromTheDerivedRoom() throws Exception {
        User user = user();
        MediaSession session = MediaSession.active(MediaChannelKind.DIRECT, null, "channel-id", user);
        when(roomServiceClient.removeParticipant("direct-channel-id", user.getId()))
                .thenReturn(removeParticipantCall);
        when(removeParticipantCall.execute()).thenReturn(Response.success(null));

        service.disconnect(session);

        verify(roomServiceClient).removeParticipant("direct-channel-id", user.getId());
    }

    @Test
    void reportsMediaInfrastructureAsUnavailableWhenLiveKitCannotBeReached() throws Exception {
        String roomName = "direct-channel-id";
        when(roomServiceClient.listRooms(List.of(roomName))).thenReturn(listRoomsCall);
        when(listRoomsCall.execute()).thenThrow(new IOException("offline"));

        assertThrows(MediaInfrastructureUnavailableException.class,
                () -> service.prepareConnection(MediaChannelKind.DIRECT, "channel-id", user()));
    }

    private User user() {
        User user = new User("Test User", "test@example.test", "password");
        user.setId("08e6fc12-8185-4df7-b803-89eb9633654a");
        return user;
    }

    private LivekitModels.Room room(String name) {
        return LivekitModels.Room.newBuilder().setName(name).build();
    }
}
