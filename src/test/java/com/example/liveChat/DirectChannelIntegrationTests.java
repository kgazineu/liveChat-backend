package com.example.liveChat;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.User;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DirectChannelIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TokenService tokens;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void eitherParticipantGetsTheSamePrivateChannelAndCanListIt() throws Exception {
        User first = user();
        User second = user();

        String created = mvc.perform(post("/direct-channels").header("Authorization", bearer(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("participantId", second.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("participantId").value(second.getId()))
                .andExpect(jsonPath("participantName").value(second.getName()))
                .andReturn().getResponse().getContentAsString();
        String channelId = mapper.readTree(created).get("id").asText();

        mvc.perform(post("/direct-channels").header("Authorization", bearer(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("participantId", first.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(channelId))
                .andExpect(jsonPath("participantId").value(first.getId()));

        mvc.perform(get("/direct-channels").header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + channelId + "')].participantId").value(second.getId()));
        mvc.perform(get("/direct-channels/" + channelId).header("Authorization", bearer(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("participantId").value(first.getId()));
    }

    @Test
    void onlyParticipantsCanReadAPrivateChannel() throws Exception {
        User first = user();
        User second = user();
        User outsider = user();
        String channelId = createChannel(first, second);

        mvc.perform(get("/direct-channels/" + channelId).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/direct-channels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingSelfAndUnknownParticipants() throws Exception {
        User currentUser = user();

        mvc.perform(post("/direct-channels").header("Authorization", bearer(currentUser))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/direct-channels").header("Authorization", bearer(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("participantId", currentUser.getId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/direct-channels").header("Authorization", bearer(currentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("participantId", UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    private String createChannel(User owner, User participant) throws Exception {
        String body = mvc.perform(post("/direct-channels").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("participantId", participant.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(body);
        assertThat(json.get("id").asText()).isNotBlank();
        return json.get("id").asText();
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user).token();
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", passwordEncoder.encode("password")));
    }
}
