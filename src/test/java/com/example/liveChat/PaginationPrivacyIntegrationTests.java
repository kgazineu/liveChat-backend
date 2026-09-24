package com.example.liveChat;

import com.example.liveChat.infra.security.TokenService;
import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaginationPrivacyIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private TokenService tokens;
    @Autowired private UserRepository users;
    @Autowired private FriendshipRepository friendships;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void userEndpointsExposeEmailOnlyForCurrentUserAndValidatePagination() throws Exception {
        User currentUser = user("Current");
        User searchable = user("Searchable");

        mvc.perform(get("/users/me").header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(currentUser.getId()))
                .andExpect(jsonPath("$.email").value(currentUser.getEmail()));

        mvc.perform(get("/users/search").param("email", searchable.getEmail())
                        .header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(searchable.getId()))
                .andExpect(jsonPath("$[0].name").value(searchable.getName()))
                .andExpect(jsonPath("$[0].email").doesNotExist());

        mvc.perform(get("/users").header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.last").isBoolean())
                .andExpect(jsonPath("$.content[0].email").doesNotExist());

        mvc.perform(get("/users").param("size", "100").header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/users").param("size", "101").header("Authorization", bearer(currentUser)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/users").param("size", "0").header("Authorization", bearer(currentUser)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/users").param("page", "-1").header("Authorization", bearer(currentUser)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/users").param("page", "invalid").header("Authorization", bearer(currentUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void friendshipsAndRequestsArePagedWithDeterministicNewestFirstOrdering() throws Exception {
        User currentUser = user("Current");
        User olderFriend = user("Older friend");
        User newerFriend = user("Newer friend");
        acceptedFriendship(olderFriend, currentUser, LocalDateTime.of(2026, 1, 1, 10, 0));
        acceptedFriendship(currentUser, newerFriend, LocalDateTime.of(2026, 1, 2, 10, 0));

        mvc.perform(get("/friendships").param("size", "1").header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newerFriend.getId()))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/friendships").param("page", "1").param("size", "1")
                        .header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(olderFriend.getId()))
                .andExpect(jsonPath("$.last").value(true));

        User olderRequester = user("Older requester");
        User newerRequester = user("Newer requester");
        pendingFriendship(olderRequester, currentUser, LocalDateTime.of(2026, 1, 3, 10, 0));
        pendingFriendship(newerRequester, currentUser, LocalDateTime.of(2026, 1, 4, 10, 0));

        mvc.perform(get("/friendships/requests").param("size", "1")
                        .header("Authorization", bearer(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requesterId").value(newerRequester.getId()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    private void acceptedFriendship(User requester, User addressee, LocalDateTime createdAt) {
        Friendship friendship = new Friendship(requester, addressee);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setCreatedAt(createdAt);
        friendships.saveAndFlush(friendship);
    }

    private void pendingFriendship(User requester, User addressee, LocalDateTime createdAt) {
        Friendship friendship = new Friendship(requester, addressee);
        friendship.setCreatedAt(createdAt);
        friendships.saveAndFlush(friendship);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user).token();
    }

    private User user(String name) {
        return users.saveAndFlush(new User(name, UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("password")));
    }
}
