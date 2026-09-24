package com.example.liveChat;

import com.example.liveChat.dto.UserLoginResponseDTO;
import com.example.liveChat.infra.mail.PasswordResetMailSender;
import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.ChannelMessage;
import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.DirectMessage;
import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.LegacyMessage;
import com.example.liveChat.models.MediaChannelKind;
import com.example.liveChat.models.MediaSession;
import com.example.liveChat.models.PasswordResetToken;
import com.example.liveChat.models.PendingProfileUpdate;
import com.example.liveChat.models.Server;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.ServerInvite;
import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.ChannelMessageRepository;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.DirectMessageRepository;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.LegacyMessageRepository;
import com.example.liveChat.repositories.PasswordResetTokenRepository;
import com.example.liveChat.repositories.PendingProfileUpdateRepository;
import com.example.liveChat.repositories.RefreshTokenRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerInviteRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.services.MediaSessionStore;
import com.example.liveChat.services.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AccountSoftDeleteIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TokenService tokens;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository users;
    @Autowired private ServerRepository servers;
    @Autowired private ServerMemberRepository members;
    @Autowired private ServerChannelRepository channels;
    @Autowired private ChannelMessageRepository channelMessages;
    @Autowired private ServerInviteRepository serverInvites;
    @Autowired private FriendshipRepository friendships;
    @Autowired private DirectChannelRepository directChannels;
    @Autowired private DirectMessageRepository directMessages;
    @Autowired private LegacyMessageRepository legacyMessages;
    @Autowired private PasswordResetTokenRepository passwordResetTokens;
    @Autowired private PendingProfileUpdateRepository pendingProfileUpdates;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private MediaSessionStore mediaSessionStore;

    @MockitoBean private PasswordResetMailSender passwordResetMailSender;

    @BeforeEach
    void resetMocks() {
        reset(passwordResetMailSender);
    }

    @Test
    void deletingOwnerTransfersOwnershipToOldestActiveMemberAndPreservesChannelHistory() throws Exception {
        User owner = user("Owner");
        User oldestMember = user("Oldest");
        User newerMember = user("Newer");
        Server server = servers.saveAndFlush(new Server("Equipe", owner));
        members.saveAndFlush(new ServerMember(server, owner, ServerRole.OWNER));
        ServerMember oldestMembership = members.saveAndFlush(new ServerMember(server, oldestMember, ServerRole.MEMBER));
        members.saveAndFlush(new ServerMember(server, newerMember, ServerRole.MEMBER));
        ServerChannel channel = channels.saveAndFlush(new ServerChannel(server, "geral", ChannelType.TEXT, 0));
        ChannelMessage message = channelMessages.saveAndFlush(new ChannelMessage(channel, owner, "histórico"));

        deleteAccount(owner);

        Server updatedServer = servers.findById(server.getId()).orElseThrow();
        assertThat(updatedServer.getOwner().getId()).isEqualTo(oldestMember.getId());
        assertThat(members.findById(oldestMembership.getId())).isPresent()
                .get().extracting(ServerMember::getRole).isEqualTo(ServerRole.OWNER);
        assertThat(members.findByServerIdAndUserId(server.getId(), owner.getId())).isEmpty();
        assertThat(channelMessages.findById(message.getId())).isPresent();

        mvc.perform(get(messagePath(server.getId(), channel.getId())).header("Authorization", bearer(oldestMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].authorName").value("Usuário excluído"));
        mvc.perform(post("/servers/" + server.getId() + "/channels").header("Authorization", bearer(oldestMember))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "novo", "type", "TEXT"))))
                .andExpect(status().isCreated());
    }

    @Test
    void deletingSoleOwnerRemovesServerAndItsDependenciesInSafeOrder() throws Exception {
        User owner = user("Owner");
        Server server = servers.saveAndFlush(new Server("Sem sucessor", owner));
        ServerMember membership = members.saveAndFlush(new ServerMember(server, owner, ServerRole.OWNER));
        ServerChannel channel = channels.saveAndFlush(new ServerChannel(server, "geral", ChannelType.TEXT, 0));
        ChannelMessage message = channelMessages.saveAndFlush(new ChannelMessage(channel, owner, "descartada"));

        deleteAccount(owner);

        assertThat(channelMessages.findById(message.getId())).isEmpty();
        assertThat(channels.findById(channel.getId())).isEmpty();
        assertThat(members.findById(membership.getId())).isEmpty();
        assertThat(servers.findById(server.getId())).isEmpty();
        assertTombstone(owner);
    }

    @Test
    void deletingMemberKeepsDirectAndLegacyHistoryButRemovesMembership() throws Exception {
        User deletedUser = user("Deleted");
        User survivor = user("Survivor");
        Server thirdPartyServer = servers.saveAndFlush(new Server("Terceiro", survivor));
        members.saveAndFlush(new ServerMember(thirdPartyServer, survivor, ServerRole.OWNER));
        members.saveAndFlush(new ServerMember(thirdPartyServer, deletedUser, ServerRole.MEMBER));
        DirectChannel directChannel = directChannels.saveAndFlush(new DirectChannel(deletedUser, survivor));
        mediaSessionStore.saveActive(MediaSession.active(MediaChannelKind.DIRECT, null, directChannel.getId(), deletedUser));
        DirectMessage directMessage = directMessages.saveAndFlush(
                new DirectMessage(directChannel, deletedUser, "mensagem preservada"));
        LegacyMessage legacyMessage = legacyMessages.saveAndFlush(
                new LegacyMessage(deletedUser, survivor, "legado preservado", LocalDateTime.now()));

        deleteAccount(deletedUser);

        assertThat(members.findByServerIdAndUserId(thirdPartyServer.getId(), deletedUser.getId())).isEmpty();
        assertThat(servers.findById(thirdPartyServer.getId())).isPresent();
        assertThat(directChannels.findById(directChannel.getId())).isPresent();
        assertThat(directMessages.findById(directMessage.getId())).isPresent();
        assertThat(legacyMessages.findById(legacyMessage.getId())).isPresent();
        assertThat(mediaSessionStore.findByUserId(deletedUser.getId())).isEmpty();

        mvc.perform(get("/direct-channels/" + directChannel.getId()).header("Authorization", bearer(survivor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("participantName").value("Usuário excluído"));
        mvc.perform(get("/direct-channels/" + directChannel.getId() + "/messages")
                        .header("Authorization", bearer(survivor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].authorName").value("Usuário excluído"));
    }

    @Test
    void tombstoneIsHiddenAndCannotAuthenticateResetOrEnterNewRelationships() throws Exception {
        User deletedUser = user("Deleted");
        User activeUser = user("Active");
        String originalEmail = deletedUser.getEmail();
        UserLoginResponseDTO login = refreshTokenService.issue(deletedUser);

        String resetRawToken = "R".repeat(43);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(hash(resetRawToken));
        resetToken.setUser(deletedUser);
        resetToken.setExpiresAt(Instant.now().plusSeconds(600));
        passwordResetTokens.saveAndFlush(resetToken);

        String profileRawToken = "P".repeat(43);
        PendingProfileUpdate pendingUpdate = new PendingProfileUpdate();
        pendingUpdate.setTokenHash(hash(profileRawToken));
        pendingUpdate.setUser(deletedUser);
        pendingUpdate.setRequestedName("Novo nome");
        pendingUpdate.setExpiresAt(Instant.now().plusSeconds(600));
        pendingProfileUpdates.saveAndFlush(pendingUpdate);

        friendships.saveAndFlush(new Friendship(activeUser, deletedUser));
        Server activeServer = servers.saveAndFlush(new Server("Ativo", activeUser));
        members.saveAndFlush(new ServerMember(activeServer, activeUser, ServerRole.OWNER));
        serverInvites.saveAndFlush(new ServerInvite(activeServer, activeUser, deletedUser));

        mvc.perform(delete("/users/" + deletedUser.getId()).header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isNoContent());

        User tombstone = assertTombstone(deletedUser);
        assertThat(passwordEncoder.matches("password", tombstone.getPassword())).isFalse();
        assertThat(refreshTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(deletedUser.getId()));
        assertThat(passwordResetTokens.findAll()).noneMatch(token -> token.getUser().getId().equals(deletedUser.getId()));
        assertThat(pendingProfileUpdates.findAll()).noneMatch(update -> update.getUser().getId().equals(deletedUser.getId()));
        assertThat(friendships.findAll()).noneMatch(friendship -> involves(friendship, deletedUser));
        assertThat(serverInvites.findAll()).noneMatch(invite -> invite.getStatus().name().equals("PENDING")
                && (invite.getInviter().getId().equals(deletedUser.getId())
                || invite.getInvitee().getId().equals(deletedUser.getId())));

        login(originalEmail).andExpect(status().isUnauthorized());
        login(tombstone.getEmail()).andExpect(status().isUnauthorized());
        mvc.perform(post("/users/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", login.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/users/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", originalEmail))))
                .andExpect(status().isAccepted());
        mvc.perform(post("/users/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", tombstone.getEmail()))))
                .andExpect(status().isAccepted());
        mvc.perform(post("/users/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", resetRawToken, "password", "new-password"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/users/profile-update/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", profileRawToken))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(passwordResetMailSender);

        mvc.perform(get("/users/search").param("email", tombstone.getEmail())
                        .header("Authorization", bearer(activeUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/users").header("Authorization", bearer(activeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + deletedUser.getId() + "')]").isEmpty());
        mvc.perform(post("/direct-channels").header("Authorization", bearer(activeUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("participantId", deletedUser.getId()))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/friendships/send").header("Authorization", bearer(activeUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("targetUserId", deletedUser.getId()))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/servers/" + activeServer.getId() + "/invites")
                        .header("Authorization", bearer(activeUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("friendId", deletedUser.getId()))))
                .andExpect(status().isBadRequest());
    }

    private User assertTombstone(User original) {
        User tombstone = users.findById(original.getId()).orElseThrow();
        assertThat(tombstone.getName()).isEqualTo("Usuário excluído");
        assertThat(tombstone.getEmail()).isEqualTo("deleted-" + original.getId() + "@deleted.invalid");
        assertThat(tombstone.getDeletedAt()).isNotNull();
        assertThat(tombstone.getCredentialsVersion()).isEqualTo(original.getCredentialsVersion() + 1);
        assertThat(tombstone.isEnabled()).isFalse();
        return tombstone;
    }

    private boolean involves(Friendship friendship, User user) {
        return friendship.getRequester().getId().equals(user.getId())
                || friendship.getAddressee().getId().equals(user.getId());
    }

    private void deleteAccount(User user) throws Exception {
        mvc.perform(delete("/users/" + user.getId()).header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email) throws Exception {
        return mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "password"))));
    }

    private String messagePath(String serverId, String channelId) {
        return "/servers/" + serverId + "/channels/" + channelId + "/messages";
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user).token();
    }

    private User user(String name) {
        return users.saveAndFlush(new User(name, UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("password")));
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
