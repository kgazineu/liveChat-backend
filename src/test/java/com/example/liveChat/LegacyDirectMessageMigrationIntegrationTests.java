package com.example.liveChat;

import com.example.liveChat.models.DirectMessage;
import com.example.liveChat.models.LegacyMessage;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.DirectMessageRepository;
import com.example.liveChat.repositories.LegacyMessageRepository;
import com.example.liveChat.repositories.UserRepository;
import com.example.liveChat.services.LegacyDirectMessageMigration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LegacyDirectMessageMigrationIntegrationTests {
    @Autowired private LegacyDirectMessageMigration migration;
    @Autowired private LegacyMessageRepository legacyMessages;
    @Autowired private DirectMessageRepository directMessages;
    @Autowired private UserRepository users;

    @Test
    void copiesLegacyHistoryIntoItsDirectChannelOnlyOnce() {
        User sender = user();
        User receiver = user();
        LocalDateTime sentAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        LegacyMessage legacy = legacyMessages.save(new LegacyMessage(sender, receiver, "histórico preservado", sentAt));

        migration.migrateLegacyMessages();
        DirectMessage migrated = directMessages.findAll().stream()
                .filter(message -> legacy.getId().equals(message.getLegacyMessageId()))
                .findFirst().orElseThrow();
        assertThat(migrated.getContent()).isEqualTo("histórico preservado");
        assertThat(migrated.getAuthor().getId()).isEqualTo(sender.getId());
        assertThat(migrated.getDirectChannel().hasParticipant(receiver.getId())).isTrue();
        assertThat(migrated.getCreatedAt()).isEqualTo(sentAt.toInstant(ZoneOffset.UTC));

        migration.migrateLegacyMessages();
        assertThat(directMessages.findAll())
                .filteredOn(message -> legacy.getId().equals(message.getLegacyMessageId()))
                .hasSize(1);
    }

    private User user() {
        return users.save(new User("Test", UUID.randomUUID() + "@example.test", "hash"));
    }
}
