package com.example.liveChat.models;

import jakarta.persistence.Column;
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
@Table(name = "TB_DIRECT_MESSAGE")
@Getter
@NoArgsConstructor
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "direct_channel_id", nullable = false)
    private DirectChannel directChannel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(unique = true)
    private Long legacyMessageId;

    public DirectMessage(DirectChannel directChannel, User author, String content) {
        this.directChannel = directChannel;
        this.author = author;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public DirectMessage(DirectChannel directChannel, User author, String content, Instant createdAt,
                         Long legacyMessageId) {
        this.directChannel = directChannel;
        this.author = author;
        this.content = content;
        this.createdAt = createdAt;
        this.legacyMessageId = legacyMessageId;
    }
}
