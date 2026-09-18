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
@Table(name = "TB_SERVER_INVITE", uniqueConstraints =
        @UniqueConstraint(columnNames = {"server_id", "invitee_id"}))
@Getter
@NoArgsConstructor
public class ServerInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    private ServerInviteStatus status;

    private Instant createdAt;

    private Instant acceptedAt;

    public ServerInvite(Server server, User inviter, User invitee) {
        this.server = server;
        this.inviter = inviter;
        this.invitee = invitee;
        this.status = ServerInviteStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void accept() {
        this.status = ServerInviteStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }
}
