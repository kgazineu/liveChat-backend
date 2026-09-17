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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "TB_CHANNEL")
@Getter
@NoArgsConstructor
public class ServerChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    private String name;

    @Enumerated(EnumType.STRING)
    private ChannelType type;

    private int position;

    private Instant createdAt;

    public ServerChannel(Server server, String name, ChannelType type, int position) {
        this.server = server;
        this.name = name;
        this.type = type;
        this.position = position;
        this.createdAt = Instant.now();
    }
}
