package com.example.liveChat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void publishesCompletePublicDocumentation() throws Exception {
        JsonNode document = objectMapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.path("info").path("title").asText()).isEqualTo("LiveChat API");
        assertThat(document.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(document.path("paths").propertyStream().map(entry -> entry.getKey()).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "/users/register", "/users/login", "/users/refresh", "/users", "/users/{id}",
                        "/users/search", "/users/me", "/friendships/send", "/friendships/{id}/accept",
                        "/friendships/{id}/reject", "/friendships", "/friendships/requests",
                        "/messages/{targetUserId}", "/actuator/health", "/ws"));

        assertThat(document.at("/paths/~1users~1login/post/security").isMissingNode()).isTrue();
        assertThat(document.at("/paths/~1users~1me/get/security/0/bearerAuth").isArray()).isTrue();
        assertThat(document.at("/paths/~1friendships/get/security/0/bearerAuth").isArray()).isTrue();
        assertThat(document.at("/paths/~1ws/get/responses/101/description").asText())
                .contains("WebSocket");
        assertThat(document.at("/components/schemas/UserLoginResponseDTO/properties/refreshToken/description").asText())
                .contains("renovação");
        assertThat(document.at("/components/schemas/MessageRequestDTO/properties/receiverId/description").asText())
                .contains("destinatário");
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
