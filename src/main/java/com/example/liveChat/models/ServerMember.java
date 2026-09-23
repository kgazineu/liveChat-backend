package com.example.liveChat.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "TB_SERVER_MEMBER", uniqueConstraints =
        @UniqueConstraint(columnNames = {"server_id", "user_id"}))
@Getter
@NoArgsConstructor
public class ServerMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private ServerRole role;

    private Instant joinedAt;

    public ServerMember(Server server, User user, ServerRole role) {
        this.server = server;
        this.user = user;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public void promoteToOwner() {
        if (user.getDeletedAt() != null) {
            throw new IllegalStateException("A deleted user cannot own a server");
        }
        this.role = ServerRole.OWNER;
    }
}
