package com.example.liveChat;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.Server;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.services.MediaPresenceService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MediaPresenceControllerIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TokenService tokens;
    @Autowired private UserRepository users;
    @Autowired private ServerRepository servers;
    @Autowired private ServerMemberRepository members;
    @Autowired private ServerChannelRepository channels;
    @Autowired private DirectChannelRepository directChannels;
    @Autowired private MediaPresenceService mediaPresenceService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void memberCanJoinSwitchUpdateAndLeaveVoiceChannel() throws Exception {
        User owner = user();
        User member = user();
        User outsider = user();
        Server server = server(owner, member);
        ServerChannel firstVoice = channel(server, "Geral", ChannelType.VOICE, 0);
        ServerChannel secondVoice = channel(server, "Estudo", ChannelType.VOICE, 1);
        ServerChannel text = channel(server, "texto", ChannelType.TEXT, 2);

        String joinBody = mvc.perform(post(serverPath(server, firstVoice)).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("channelKind").value("SERVER_VOICE"))
                .andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("microphoneEnabled").value(true))
                .andExpect(jsonPath("connection.url").value("ws://localhost:7880"))
                .andExpect(jsonPath("connection.roomName").value("server-voice-" + firstVoice.getId()))
                .andReturn().getResponse().getContentAsString();
        assertLiveKitToken(joinBody, owner, "server-voice-" + firstVoice.getId());

        mvc.perform(get(serverPath(server, firstVoice)).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(owner.getId()))
                .andExpect(jsonPath("$[0].connection").doesNotExist());

        mvc.perform(patch(serverPath(server, firstVoice) + "/me").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("microphoneEnabled", false, "cameraEnabled", true,
                                "screenShareEnabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("microphoneEnabled").value(false))
                .andExpect(jsonPath("cameraEnabled").value(true))
                .andExpect(jsonPath("screenShareEnabled").value(true));

        mvc.perform(post(serverPath(server, secondVoice)).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("channelId").value(secondVoice.getId()));
        mvc.perform(get(serverPath(server, firstVoice)).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(serverPath(server, secondVoice)).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channelId").value(secondVoice.getId()));

        mvc.perform(post(serverPath(server, text)).header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(serverPath(server, secondVoice)).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());

        mvc.perform(delete(serverPath(server, secondVoice)).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get(serverPath(server, secondVoice)).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void onlyParticipantsCanUsePrivateMediaPresence() throws Exception {
        User first = user();
        User second = user();
        User outsider = user();
        DirectChannel channel = directChannels.save(new DirectChannel(first, second));
        String basePath = "/direct-channels/" + channel.getId() + "/media-sessions";

        String joinBody = mvc.perform(post(basePath).header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("channelKind").value("DIRECT"))
                .andExpect(jsonPath("connection.roomName").value("direct-" + channel.getId()))
                .andReturn().getResponse().getContentAsString();
        assertLiveKitToken(joinBody, first, "direct-" + channel.getId());
        mvc.perform(get(basePath).header("Authorization", bearer(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(first.getId()));
        mvc.perform(patch(basePath + "/me").header("Authorization", bearer(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(basePath + "/me").header("Authorization", bearer(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("cameraEnabled", true))))
                .andExpect(status().isConflict());
        mvc.perform(post(basePath).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullVoiceChannelRejectsTheSwitchAndKeepsThePreviousPresence() throws Exception {
        User owner = user();
        User switchingUser = user();
        Server server = server(owner, switchingUser);
        ServerChannel firstVoice = channel(server, "Primeira", ChannelType.VOICE, 0);
        ServerChannel fullVoice = channel(server, "Cheia", ChannelType.VOICE, 1);
        String firstPath = serverPath(server, firstVoice);
        String fullPath = serverPath(server, fullVoice);

        mvc.perform(post(firstPath).header("Authorization", bearer(switchingUser))).andExpect(status().isOk());
        for (int index = 0; index < 5; index++) {
            User occupant = user();
            members.save(new ServerMember(server, occupant, ServerRole.MEMBER));
            mvc.perform(post(fullPath).header("Authorization", bearer(occupant))).andExpect(status().isOk());
        }

        mvc.perform(post(fullPath).header("Authorization", bearer(switchingUser)))
                .andExpect(status().isConflict());
        mvc.perform(get(firstPath).header("Authorization", bearer(switchingUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(switchingUser.getId()));
    }

    @Test
    void unexpectedDisconnectIsVisibleForTheReconnectionWindowThenExpires() throws Exception {
        User owner = user();
        Server server = server(owner);
        ServerChannel voice = channel(server, "Geral", ChannelType.VOICE, 0);
        String path = serverPath(server, voice);

        mvc.perform(post(path).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mediaPresenceService.markReconnectingAfterDisconnect(owner.getEmail());
        mvc.perform(get(path).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RECONNECTING"));

        await().untilAsserted(() -> mvc.perform(get(path).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty()));
    }

    private Server server(User owner, User... otherMembers) {
        Server server = servers.save(new Server("Equipe", owner));
        members.save(new ServerMember(server, owner, ServerRole.OWNER));
        for (User member : otherMembers) {
            members.save(new ServerMember(server, member, ServerRole.MEMBER));
        }
        return server;
    }

    private ServerChannel channel(Server server, String name, ChannelType type, int position) {
        return channels.save(new ServerChannel(server, name, type, position));
    }

    private String serverPath(Server server, ServerChannel channel) {
        return "/servers/" + server.getId() + "/channels/" + channel.getId() + "/media-sessions";
    }

    private void assertLiveKitToken(String responseBody, User user, String roomName) throws Exception {
        JsonNode response = mapper.readTree(responseBody);
        String token = response.path("connection").path("token").asText();
        Instant expiresAt = Instant.parse(response.path("connection").path("expiresAt").asText());
        DecodedJWT decoded = JWT.require(Algorithm.HMAC256("test-livekit-secret-at-least-32-characters-long"))
                .withIssuer("test-livekit-key")
                .build()
                .verify(token);

        assertEquals(user.getId(), decoded.getSubject());
        assertEquals(user.getName(), decoded.getClaim("name").asString());
        Map<String, Object> videoGrant = decoded.getClaim("video").asMap();
        assertEquals(roomName, videoGrant.get("room"));
        assertEquals(true, videoGrant.get("roomJoin"));
        assertEquals(true, videoGrant.get("canPublish"));
        assertEquals(true, videoGrant.get("canSubscribe"));
        assertEquals(false, videoGrant.get("canPublishData"));
        assertTrue(expiresAt.isAfter(Instant.now().plusSeconds(250)));
        assertFalse(expiresAt.isAfter(Instant.now().plusSeconds(310)));
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user).token();
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", passwordEncoder.encode("password")));
    }
}
