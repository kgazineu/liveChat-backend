package com.example.liveChat.services;

import com.example.liveChat.dto.LiveKitConnectionDTO;
import com.example.liveChat.exceptions.MediaInfrastructureUnavailableException;
import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;
import com.example.liveChat.models.User;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.ServerError;
import livekit.LivekitModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class LiveKitMediaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveKitMediaService.class);
    private static final int DIRECT_CHANNEL_MAX_PARTICIPANTS = 2;
    private static final List<String> PUBLISHABLE_SOURCES = List.of(
            "microphone", "camera", "screen_share", "screen_share_audio");

    private final LiveKitProperties properties;
    private final MediaPresenceProperties mediaPresenceProperties;
    private final RoomServiceClient roomServiceClient;

    public LiveKitMediaService(LiveKitProperties properties, MediaPresenceProperties mediaPresenceProperties,
                               RoomServiceClient roomServiceClient) {
        this.properties = properties;
        this.mediaPresenceProperties = mediaPresenceProperties;
        this.roomServiceClient = roomServiceClient;
    }

    public LiveKitConnectionDTO prepareConnection(MediaChannelKind channelKind, String channelId, User user) {
        String roomName = roomName(channelKind, channelId);
        int maxParticipants = channelKind == MediaChannelKind.SERVER_VOICE
                ? mediaPresenceProperties.getMaxServerParticipants()
                : DIRECT_CHANNEL_MAX_PARTICIPANTS;
        ensureRoom(roomName, maxParticipants);

        Instant expiresAt = Instant.now().plus(properties.getTokenTtl());
        AccessToken token = new AccessToken(properties.getApiKey(), properties.getApiSecret());
        token.setIdentity(user.getId());
        token.setName(user.getName());
        token.setExpiration(Date.from(expiresAt));
        token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanPublish(true), new CanSubscribe(true),
                new CanPublishData(false), new CanPublishSources(PUBLISHABLE_SOURCES));

        return new LiveKitConnectionDTO(properties.getClientUrl(), roomName, token.toJwt(), expiresAt);
    }

    public void disconnect(MediaSession session) {
        if (!properties.isRoomProvisioningEnabled()) {
            return;
        }
        String roomName = roomName(session.channelKind(), session.channelId());
        try {
            Response<Void> response = roomServiceClient.removeParticipant(roomName, session.userId()).execute();
            if (response.isSuccessful() || isNotFound(response)) {
                return;
            }
            throw unavailable("remove participant from room", roomName, response);
        } catch (IOException exception) {
            LOGGER.warn("Could not remove participant {} from LiveKit room {}", session.userId(), roomName, exception);
            throw new MediaInfrastructureUnavailableException("Media server is unavailable", exception);
        }
    }

    private String roomName(MediaChannelKind channelKind, String channelId) {
        String prefix = channelKind == MediaChannelKind.SERVER_VOICE ? "server-voice-" : "direct-";
        return prefix + channelId;
    }

    private void ensureRoom(String roomName, int maxParticipants) {
        if (!properties.isRoomProvisioningEnabled()) {
            return;
        }
        try {
            Response<List<LivekitModels.Room>> listedRooms = roomServiceClient.listRooms(List.of(roomName)).execute();
            if (!listedRooms.isSuccessful()) {
                throw unavailable("list room", roomName, listedRooms);
            }
            if (listedRooms.body() != null && !listedRooms.body().isEmpty()) {
                return;
            }

            int emptyTimeoutSeconds = Math.toIntExact(properties.getRoomEmptyTimeout().toSeconds());
            Response<LivekitModels.Room> createdRoom = roomServiceClient
                    .createRoom(roomName, emptyTimeoutSeconds, maxParticipants)
                    .execute();
            if (!createdRoom.isSuccessful()) {
                Response<List<LivekitModels.Room>> roomAfterConflict = roomServiceClient
                        .listRooms(List.of(roomName))
                        .execute();
                if (roomAfterConflict.isSuccessful() && roomAfterConflict.body() != null
                        && !roomAfterConflict.body().isEmpty()) {
                    return;
                }
                throw unavailable("create room", roomName, createdRoom);
            }
        } catch (IOException | ArithmeticException exception) {
            LOGGER.warn("LiveKit room provisioning failed for room {}", roomName, exception);
            throw new MediaInfrastructureUnavailableException("Media server is unavailable", exception);
        }
    }

    private boolean isNotFound(Response<?> response) {
        ServerError error = ServerError.from(response);
        return response.code() == 404 || error != null && "not_found".equals(error.getCode());
    }

    private MediaInfrastructureUnavailableException unavailable(String operation, String roomName,
                                                                 Response<?> response) {
        ServerError serverError = ServerError.from(response);
        String detail = serverError == null
                ? "HTTP " + response.code()
                : serverError.getCode() + ": " + serverError.getMessage();
        LOGGER.warn("Could not {} {} in LiveKit: {}", operation, roomName, detail);
        return new MediaInfrastructureUnavailableException("Media server is unavailable");
    }
}
