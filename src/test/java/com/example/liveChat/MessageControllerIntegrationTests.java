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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MessageControllerIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TokenService tokens;
    @Autowired private UserRepository users;
    @Autowired private DirectChannelRepository directChannels;
    @Autowired private ServerRepository servers;
    @Autowired private ServerMemberRepository members;
    @Autowired private ServerChannelRepository channels;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void directMessagesArePersistedAndVisibleOnlyToParticipants() throws Exception {
        User author = user();
        User participant = user();
        User outsider = user();
        DirectChannel channel = directChannels.save(new DirectChannel(author, participant));

        mvc.perform(post("/direct-channels/" + channel.getId() + "/messages").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "  olá em privado  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("channelId").value(channel.getId()))
                .andExpect(jsonPath("content").value("olá em privado"))
                .andExpect(jsonPath("authorId").value(author.getId()));

        mvc.perform(get("/direct-channels/" + channel.getId() + "/messages").header("Authorization", bearer(participant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("olá em privado"));
        mvc.perform(post("/direct-channels/" + channel.getId() + "/messages").header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "vazamento"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/direct-channels/" + channel.getId() + "/messages").header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void serverTextMessagesRequireMembershipAndRejectVoiceChannels() throws Exception {
        User owner = user();
        User member = user();
        User outsider = user();
        Server server = servers.save(new Server("Equipe", owner));
        members.save(new ServerMember(server, owner, ServerRole.OWNER));
        members.save(new ServerMember(server, member, ServerRole.MEMBER));
        ServerChannel textChannel = channels.save(new ServerChannel(server, "geral", ChannelType.TEXT, 0));
        ServerChannel voiceChannel = channels.save(new ServerChannel(server, "sala", ChannelType.VOICE, 1));

        mvc.perform(post(messagePath(server, textChannel)).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "mensagem do grupo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("channelId").value(textChannel.getId()))
                .andExpect(jsonPath("authorId").value(member.getId()));
        mvc.perform(get(messagePath(server, textChannel)).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("mensagem do grupo"));
        mvc.perform(get(messagePath(server, textChannel)).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(post(messagePath(server, voiceChannel)).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "não permitido"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageContentIsRequiredAndRoutesRequireAuthentication() throws Exception {
        User author = user();
        User participant = user();
        DirectChannel channel = directChannels.save(new DirectChannel(author, participant));

        mvc.perform(post("/direct-channels/" + channel.getId() + "/messages"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/direct-channels/" + channel.getId() + "/messages").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "   "))))
                .andExpect(status().isBadRequest());
    }

    private String messagePath(Server server, ServerChannel channel) {
        return "/servers/" + server.getId() + "/channels/" + channel.getId() + "/messages";
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user).token();
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", passwordEncoder.encode("password")));
    }
}
