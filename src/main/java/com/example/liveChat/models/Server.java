package com.example.liveChat.models;

import jakarta.persistence.Entity;
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
@Table(name = "TB_SERVER")
@Getter
@NoArgsConstructor
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    private Instant createdAt;

    public Server(String name, User owner) {
        this.name = name;
        this.owner = owner;
        this.createdAt = Instant.now();
    }

    public void transferOwnership(User newOwner) {
        if (newOwner == null || newOwner.getDeletedAt() != null) {
            throw new IllegalArgumentException("New owner must be active");
        }
        if (owner.getId().equals(newOwner.getId())) {
            throw new IllegalArgumentException("New owner must be a different user");
        }
        this.owner = newOwner;
    }
}
