package com.example.liveChat.infra.websockets;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class WebsocketSecurityInterceptor implements ChannelInterceptor {
    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            throw new AccessDeniedException("Invalid STOMP message");
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new BadCredentialsException("Invalid WebSocket credentials");
            }
            String email = tokenService.validateToken(authHeader.substring(7));
            if (email == null || email.isBlank()) {
                throw new BadCredentialsException("Invalid WebSocket credentials");
            }
            var user = userRepository.findActiveByEmailIgnoreCase(email)
                    .orElseThrow(() -> new BadCredentialsException("Invalid WebSocket credentials"));
            if (!tokenService.isTokenValidForUser(authHeader.substring(7), user)) {
                throw new BadCredentialsException("Invalid WebSocket credentials");
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(user.getEmail(), null, user.getAuthorities()));
            return message;
        }

        // Allow cleanup even when CONNECT was rejected.
        if (command == StompCommand.DISCONNECT) return message;

        if (!(accessor.getUser() instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (command == null || command == StompCommand.UNSUBSCRIBE) return message;
        if (command == StompCommand.SEND && isAllowedSendDestination(accessor.getDestination())) return message;
        if (command == StompCommand.SUBSCRIBE && isAllowedSubscribeDestination(accessor.getDestination())) return message;

        throw new AccessDeniedException("STOMP destination or command not allowed");
    }

    private boolean isAllowedSendDestination(String destination) {
        if (destination == null) return false;
        return destination.matches("^/app/direct-channels/[0-9a-fA-F-]{36}/messages$")
                || destination.matches("^/app/servers/[0-9a-fA-F-]{36}/channels/[0-9a-fA-F-]{36}/messages$");
    }

    private boolean isAllowedSubscribeDestination(String destination) {
        return "/user/queue/messages".equals(destination)
                || "/user/queue/media-presence".equals(destination)
                || "/user/queue/friendships".equals(destination)
                || "/user/queue/server-invites".equals(destination)
                || "/user/queue/server-members".equals(destination);
    }
}
