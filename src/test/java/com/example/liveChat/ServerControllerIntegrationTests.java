package com.example.liveChat;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.User;
import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.UserRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ServerControllerIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TokenService tokens;
    @Autowired private UserRepository users;
    @Autowired private ServerMemberRepository members;
    @Autowired private FriendshipRepository friendships;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void creatorBecomesOwnerAndCanListAndReadServer() throws Exception {
        User owner = user();
        String serverId = createServer(owner, "Equipe de produto");

        assertThat(members.findByServerIdAndUserId(serverId, owner.getId())).isPresent()
                .get().extracting(member -> member.getRole().name()).isEqualTo("OWNER");

        mvc.perform(get("/servers").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + serverId + "')].name").value("Equipe de produto"));
        mvc.perform(get("/servers/" + serverId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(serverId))
                .andExpect(jsonPath("role").value("OWNER"));
    }

    @Test
    void ownerCreatesTextAndVoiceChannelsInCreationOrder() throws Exception {
        User owner = user();
        String serverId = createServer(owner, "Estudo");

        mvc.perform(post("/servers/" + serverId + "/channels").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "geral", "type", "TEXT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("name").value("geral"))
                .andExpect(jsonPath("position").value(0));
        mvc.perform(post("/servers/" + serverId + "/channels").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "sala", "type", "VOICE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("position").value(1));

        mvc.perform(get("/servers/" + serverId + "/channels").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("geral"))
                .andExpect(jsonPath("$[0].type").value("TEXT"))
                .andExpect(jsonPath("$[1].name").value("sala"))
                .andExpect(jsonPath("$[1].type").value("VOICE"));
    }

    @Test
    void nonMembersCannotReadServerOrCreateChannels() throws Exception {
        User owner = user();
        User outsider = user();
        String serverId = createServer(owner, "Privado");

        mvc.perform(get("/servers/" + serverId).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/servers/" + serverId + "/channels").header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/servers/" + serverId + "/channels").header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "vazamento", "type", "TEXT"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/servers").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Anonimo"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidServerAndChannelDataReturnBadRequest() throws Exception {
        User owner = user();
        mvc.perform(post("/servers").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest());

        String serverId = createServer(owner, "Validação");
        mvc.perform(post("/servers/" + serverId + "/channels").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "geral"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberCanInviteAnAcceptedFriendWhoMustExplicitlyAcceptToJoin() throws Exception {
        User owner = user();
        User friend = user();
        makeFriends(owner, friend);
        String serverId = createServer(owner, "Comunidade");

        String inviteResponse = mvc.perform(post("/servers/" + serverId + "/invites")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("friendId", friend.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("serverId").value(serverId))
                .andExpect(jsonPath("inviterId").value(owner.getId()))
                .andExpect(jsonPath("status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long inviteId = mapper.readTree(inviteResponse).get("id").asLong();

        mvc.perform(get("/servers/invites").header("Authorization", bearer(friend)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(inviteId));
        assertThat(members.findByServerIdAndUserId(serverId, friend.getId())).isEmpty();

        mvc.perform(patch("/servers/invites/" + inviteId + "/accept").header("Authorization", bearer(friend)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(serverId))
                .andExpect(jsonPath("role").value("MEMBER"));
        assertThat(members.findByServerIdAndUserId(serverId, friend.getId())).isPresent()
                .get().extracting(member -> member.getRole().name()).isEqualTo("MEMBER");
    }

    @Test
    void inviteRequiresFriendshipAndOnlyTheInviteeCanAccept() throws Exception {
        User owner = user();
        User friend = user();
        User outsider = user();
        makeFriends(owner, friend);
        String serverId = createServer(owner, "Privado");

        mvc.perform(post("/servers/" + serverId + "/invites").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("friendId", outsider.getId()))))
                .andExpect(status().isForbidden());

        String inviteResponse = mvc.perform(post("/servers/" + serverId + "/invites")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("friendId", friend.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long inviteId = mapper.readTree(inviteResponse).get("id").asLong();

        mvc.perform(patch("/servers/invites/" + inviteId + "/accept").header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/servers/" + serverId + "/invites").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("friendId", friend.getId()))))
                .andExpect(status().isConflict());
    }

    private String createServer(User owner, String name) throws Exception {
        String body = mvc.perform(post("/servers").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user).token();
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", passwordEncoder.encode("password")));
    }

    private void makeFriends(User first, User second) {
        Friendship friendship = new Friendship(first, second);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendships.save(friendship);
    }
}
