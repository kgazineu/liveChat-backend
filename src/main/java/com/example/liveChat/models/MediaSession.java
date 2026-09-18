package com.example.liveChat.models;

import java.time.Instant;

/**
 * Estado efêmero de presença de mídia. Esta estrutura é guardada no Redis, nunca no PostgreSQL.
 */
public record MediaSession(
        MediaChannelKind channelKind,
        String serverId,
        String channelId,
        String userId,
        String userName,
        Instant joinedAt,
        boolean microphoneEnabled,
        boolean cameraEnabled,
        boolean screenShareEnabled,
        MediaSessionStatus status,
        Instant lastSeenAt,
        String reconnectionId) {

    public static MediaSession active(MediaChannelKind channelKind, String serverId, String channelId, User user) {
        Instant now = Instant.now();
        return new MediaSession(channelKind, serverId, channelId, user.getId(), user.getName(), now,
                true, false, false, MediaSessionStatus.ACTIVE, now, null);
    }

    public boolean belongsTo(MediaChannelKind expectedKind, String expectedChannelId) {
        return channelKind == expectedKind && channelId.equals(expectedChannelId);
    }

    public MediaSession reactivate() {
        return new MediaSession(channelKind, serverId, channelId, userId, userName, joinedAt,
                microphoneEnabled, cameraEnabled, screenShareEnabled, MediaSessionStatus.ACTIVE, Instant.now(), null);
    }

    public MediaSession reconnecting(String newReconnectionId) {
        return new MediaSession(channelKind, serverId, channelId, userId, userName, joinedAt,
                microphoneEnabled, cameraEnabled, screenShareEnabled, MediaSessionStatus.RECONNECTING, Instant.now(),
                newReconnectionId);
    }

    public MediaSession withMediaState(Boolean microphoneEnabled, Boolean cameraEnabled, Boolean screenShareEnabled) {
        return new MediaSession(channelKind, serverId, channelId, userId, userName, joinedAt,
                microphoneEnabled == null ? this.microphoneEnabled : microphoneEnabled,
                cameraEnabled == null ? this.cameraEnabled : cameraEnabled,
                screenShareEnabled == null ? this.screenShareEnabled : screenShareEnabled,
                status, Instant.now(), reconnectionId);
    }
}
