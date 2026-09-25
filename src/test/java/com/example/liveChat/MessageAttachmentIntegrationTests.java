package com.example.liveChat;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.infra.storage.InMemoryAttachmentObjectStorage;
import com.example.liveChat.models.ChannelType;
import com.example.liveChat.models.DirectChannel;
import com.example.liveChat.models.Server;
import com.example.liveChat.models.ServerChannel;
import com.example.liveChat.models.ServerMember;
import com.example.liveChat.models.ServerRole;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectChannelRepository;
import com.example.liveChat.repositories.DirectMessageRepository;
import com.example.liveChat.repositories.MessageAttachmentRepository;
import com.example.liveChat.repositories.ServerChannelRepository;
import com.example.liveChat.repositories.ServerMemberRepository;
import com.example.liveChat.repositories.ServerRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MessageAttachmentIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TokenService tokens;
    @Autowired private UserRepository users;
    @Autowired private DirectChannelRepository directChannels;
    @Autowired private DirectMessageRepository directMessages;
    @Autowired private ServerRepository servers;
    @Autowired private ServerMemberRepository members;
    @Autowired private ServerChannelRepository channels;
    @Autowired private MessageAttachmentRepository attachments;
    @Autowired private InMemoryAttachmentObjectStorage objectStorage;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void directUploadCanBeConsumedByAnAttachmentOnlyMessageAndListed() throws Exception {
        User author = user();
        User participant = user();
        DirectChannel channel = directChannels.save(new DirectChannel(author, participant));

        JsonNode reservation = reserveDirect(author, channel, "photo.png", "image/png", 1024, 640, 480);
        String attachmentId = reservation.get("attachmentId").asText();
        assertThat(reservation.get("uploadUrl").asText()).startsWith("memory://");
        assertThat(reservation.get("requiredHeaders").get("content-type").asText()).isEqualTo("image/png");
        assertThat(reservation.get("requiredHeaders").get("x-amz-meta-owner-id").asText()).isEqualTo(author.getId());
        assertThat(reservation.get("requiredHeaders").get("x-amz-meta-upload-id").asText()).isEqualTo(attachmentId);

        mvc.perform(post("/direct-channels/" + channel.getId() + "/messages")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("attachmentIds", List.of(attachmentId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(""))
                .andExpect(jsonPath("$.attachments[0].id").value(attachmentId))
                .andExpect(jsonPath("$.attachments[0].originalName").value("photo.png"))
                .andExpect(jsonPath("$.attachments[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.attachments[0].size").value(1024))
                .andExpect(jsonPath("$.attachments[0].width").value(640))
                .andExpect(jsonPath("$.attachments[0].height").value(480))
                .andExpect(jsonPath("$.attachments[0].downloadUrl").isNotEmpty())
                .andExpect(jsonPath("$.attachments[0].downloadExpiresAt").isNotEmpty());

        mvc.perform(get("/direct-channels/" + channel.getId() + "/messages")
                        .header("Authorization", bearer(participant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].attachments[0].id").value(attachmentId))
                .andExpect(jsonPath("$.content[0].attachments[0].downloadUrl").isNotEmpty());

        assertThat(attachments.findById(attachmentId)).get()
                .satisfies(attachment -> {
                    assertThat(attachment.isPending()).isFalse();
                    assertThat(attachment.getDirectMessage()).isNotNull();
                    assertThat(attachment.getObjectKey()).doesNotContain("photo.png");
                });
    }

    @Test
    void messageRejectsAReservationWhoseUploadWasNotCompleted() throws Exception {
        User author = user();
        User participant = user();
        DirectChannel channel = directChannels.save(new DirectChannel(author, participant));
        String attachmentId = reserveDirect(author, channel, "missing.png", "image/png", 512, 20, 20)
                .get("attachmentId").asText();
        var attachment = attachments.findById(attachmentId).orElseThrow();
        objectStorage.delete(attachment.getObjectKey());

        mvc.perform(post("/direct-channels/" + channel.getId() + "/messages")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("attachmentIds", List.of(attachmentId)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Upload has not been completed or is no longer available"));

        assertThat(directMessages.findByDirectChannelIdOrderByCreatedAtDescIdDesc(
                channel.getId(), org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(attachments.findById(attachmentId)).get().matches(com.example.liveChat.models.MessageAttachment::isPending);
    }

    @Test
    void serverUploadCanOnlyBeReservedAndConsumedByAMemberInItsTextChannel() throws Exception {
        User owner = user();
        User member = user();
        User outsider = user();
        Server server = servers.save(new Server("Team", owner));
        members.save(new ServerMember(server, owner, ServerRole.OWNER));
        members.save(new ServerMember(server, member, ServerRole.MEMBER));
        ServerChannel text = channels.save(new ServerChannel(server, "general", ChannelType.TEXT, 0));
        ServerChannel otherText = channels.save(new ServerChannel(server, "other", ChannelType.TEXT, 1));
        ServerChannel voice = channels.save(new ServerChannel(server, "voice", ChannelType.VOICE, 2));

        mvc.perform(post(serverUploadPath(server, text)).header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(validUpload("image.webp", "image/webp", 2048)))
                .andExpect(status().isForbidden());
        mvc.perform(post(serverUploadPath(server, voice)).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(validUpload("image.webp", "image/webp", 2048)))
                .andExpect(status().isBadRequest());

        String attachmentId = mapper.readTree(mvc.perform(post(serverUploadPath(server, text))
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpload("image.webp", "image/webp", 2048)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("attachmentId").asText();

        mvc.perform(post(messagePath(server, otherText)).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "wrong channel",
                                "attachmentIds", List.of(attachmentId)))))
                .andExpect(status().isForbidden());

        mvc.perform(post(messagePath(server, text)).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "wrong author",
                                "attachmentIds", List.of(attachmentId)))))
                .andExpect(status().isForbidden());

        mvc.perform(post(messagePath(server, text)).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", "valid",
                                "attachmentIds", List.of(attachmentId)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].id").value(attachmentId));
    }

    @Test
    void uploadReservationRejectsUnsupportedOrInconsistentMetadata() throws Exception {
        User author = user();
        User participant = user();
        DirectChannel channel = directChannels.save(new DirectChannel(author, participant));

        mvc.perform(reserveDirectRequest(author, channel, "payload.exe", "image/png", 100, 10, 10))
                .andExpect(status().isBadRequest());
        mvc.perform(reserveDirectRequest(author, channel, "photo.png", "application/pdf", 100, 10, 10))
                .andExpect(status().isBadRequest());
        mvc.perform(reserveDirectRequest(author, channel, "photo.png", "image/png", 10L * 1024 * 1024 + 1, 10, 10))
                .andExpect(status().isBadRequest());
        mvc.perform(reserveDirectRequest(author, channel, "photo.png", "image/png", 100, 10, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageRejectsMoreThanFourAttachmentsAndUploadBurstIsLimited() throws Exception {
        User author = user();
        User participant = user();
        DirectChannel channel = directChannels.save(new DirectChannel(author, participant));

        mvc.perform(post("/direct-channels/" + channel.getId() + "/messages")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("attachmentIds", List.of(
                                UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(), UUID.randomUUID().toString())))))
                .andExpect(status().isBadRequest());

        for (int request = 0; request < 8; request++) {
            mvc.perform(reserveDirectRequest(author, channel, "image-" + request + ".gif", "image/gif", 100, 10, 10))
                    .andExpect(status().isCreated());
        }
        mvc.perform(reserveDirectRequest(author, channel, "blocked.gif", "image/gif", 100, 10, 10))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private JsonNode reserveDirect(User user, DirectChannel channel, String originalName, String contentType,
                                   long size, Integer width, Integer height) throws Exception {
        String response = mvc.perform(reserveDirectRequest(user, channel, originalName, contentType, size, width, height))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reserveDirectRequest(
            User user, DirectChannel channel, String originalName, String contentType, long size,
            Integer width, Integer height) throws Exception {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("originalName", originalName);
        body.put("contentType", contentType);
        body.put("size", size);
        if (width != null) body.put("width", width);
        if (height != null) body.put("height", height);
        return post("/direct-channels/" + channel.getId() + "/attachments/uploads")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
    }

    private String validUpload(String originalName, String contentType, long size) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "originalName", originalName,
                "contentType", contentType,
                "size", size,
                "width", 20,
                "height", 20));
    }

    private String serverUploadPath(Server server, ServerChannel channel) {
        return "/servers/" + server.getId() + "/channels/" + channel.getId() + "/attachments/uploads";
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
