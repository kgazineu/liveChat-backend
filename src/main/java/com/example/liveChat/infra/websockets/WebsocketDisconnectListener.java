package com.example.liveChat.infra.websockets;

import com.example.liveChat.services.MediaPresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebsocketDisconnectListener {
    private final MediaPresenceService mediaPresenceService;

    public WebsocketDisconnectListener(MediaPresenceService mediaPresenceService) {
        this.mediaPresenceService = mediaPresenceService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null) {
            mediaPresenceService.markReconnectingAfterDisconnect(principal.getName());
        }
    }
}
