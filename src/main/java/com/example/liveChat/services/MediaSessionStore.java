package com.example.liveChat.services;

import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;

import java.util.List;
import java.util.Optional;

public interface MediaSessionStore {
    Optional<MediaSession> findByUserId(String userId);

    List<MediaSession> findByChannel(MediaChannelKind channelKind, String channelId);

    void saveActive(MediaSession session);

    void markReconnecting(MediaSession session);

    void delete(MediaSession session);

    Optional<MediaSession> removeExpired(String userId, String reconnectionId);
}
