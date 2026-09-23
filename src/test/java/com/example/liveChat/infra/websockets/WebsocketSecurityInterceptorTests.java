package com.example.liveChat.infra.websockets;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebsocketSecurityInterceptorTests {
    @Mock private TokenService tokenService;
    @Mock private UserRepository users;
    @InjectMocks private WebsocketSecurityInterceptor interceptor;

    @Test
    void connectRequiresBearerToken() {
        for (String header : List.of("", "Basic token")) {
            var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            if (!header.isEmpty()) accessor.setNativeHeader("Authorization", header);
            assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                    .isInstanceOf(BadCredentialsException.class);
        }
        verifyNoInteractions(tokenService, users);
    }

    @Test
    void connectRejectsInvalidTokenAndDeletedUser() {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer token");
        when(tokenService.validateToken("token")).thenReturn("");
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users);
        when(tokenService.validateToken("token")).thenReturn("missing@example.test");
        when(users.findActiveByEmailIgnoreCase("missing@example.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void connectAssociatesAuthenticatedEmailWithSession() {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer token");
        when(tokenService.validateToken("token")).thenReturn("user@example.test");
        User user = new User("User", "user@example.test", "hash");
        when(users.findActiveByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(tokenService.isTokenValidForUser("token", user)).thenReturn(true);
        interceptor.preSend(message(accessor), null);
        assertThat(accessor.getUser().getName()).isEqualTo("user@example.test");
    }

    @Test
    void connectRejectsTokenFromBeforePasswordReset() {
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer stale-token");
        User user = new User("User", "user@example.test", "hash");
        user.setCredentialsVersion(1);
        when(tokenService.validateToken("stale-token")).thenReturn(user.getEmail());
        when(users.findActiveByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenService.isTokenValidForUser("stale-token", user)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "SEND,/app/direct-channels/550e8400-e29b-41d4-a716-446655440000/messages",
            "SEND,/app/servers/550e8400-e29b-41d4-a716-446655440000/channels/6ba7b810-9dad-41d1-80b4-00c04fd430c8/messages",
            "SUBSCRIBE,/user/queue/messages",
            "SUBSCRIBE,/user/queue/friendships",
            "SUBSCRIBE,/user/queue/server-invites",
            "SUBSCRIBE,/user/queue/server-members"
    })
    void authenticatedClientCanUseMessageDestinations(StompCommand command, String destination) {
        var accessor = authenticated(command, destination);
        Message<?> message = message(accessor);
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @ParameterizedTest
    @CsvSource({"SEND,/queue/messages", "SEND,/user/queue/messages", "SEND,/app/other", "SEND,/app/chat",
            "SEND,/app/direct-channels/not-a-uuid/messages", "SEND,/app/direct-channels/550e8400-e29b-41d4-a716-446655440000/other",
            "SUBSCRIBE,/queue/messages", "SUBSCRIBE,/queue/messages-user123", "SUBSCRIBE,/queue/**",
            "SUBSCRIBE,/queue/friendships", "SUBSCRIBE,/user/queue/friendships/other",
            "SUBSCRIBE,/user/queue/server-invites-user123", "SUBSCRIBE,/user/queue/server-members/other",
            "SUBSCRIBE,/user/other/queue/messages", "SUBSCRIBE,/app/chat"})
    void authenticatedClientCannotBypassChatRouting(StompCommand command, String destination) {
        var accessor = authenticated(command, destination);
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @CsvSource({"SEND,/app/direct-channels/550e8400-e29b-41d4-a716-446655440000/messages", "SUBSCRIBE,/user/queue/messages"})
    void anonymousClientCannotSendOrSubscribe(StompCommand command, String destination) {
        var accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private StompHeaderAccessor authenticated(StompCommand command, String destination) {
        var accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken("user@example.test", null, List.of()));
        return accessor;
    }

    private Message<?> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
