package com.example.liveChat.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "TB_PENDING_PROFILE_UPDATE", uniqueConstraints = {
        @UniqueConstraint(name = "UK_PENDING_PROFILE_UPDATE_USER", columnNames = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PendingProfileUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 100)
    private String requestedName;

    @Column(length = 254)
    private String requestedEmail;

    @Column(nullable = false)
    private Instant expiresAt;
}
