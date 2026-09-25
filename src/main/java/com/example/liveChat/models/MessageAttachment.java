package com.example.liveChat.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "TB_MESSAGE_ATTACHMENT", indexes = {
        @Index(name = "ix_attachment_direct_channel", columnList = "direct_channel_id"),
        @Index(name = "ix_attachment_server_channel", columnList = "server_channel_id"),
        @Index(name = "ix_attachment_direct_message", columnList = "direct_message_id"),
        @Index(name = "ix_attachment_channel_message", columnList = "channel_message_id"),
        @Index(name = "ix_attachment_pending_expiry", columnList = "direct_message_id, channel_message_id, expires_at")
})
@Check(name = "ck_message_attachment_relations", constraints = "((direct_channel_id is not null and server_channel_id is null) or (direct_channel_id is null and server_channel_id is not null)) and (direct_message_id is null or channel_message_id is null)")
@Getter
public class MessageAttachment {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_channel_id", updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DirectChannel directChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_channel_id", updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ServerChannel serverChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_message_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DirectMessage directMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_message_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChannelMessage channelMessage;

    @Column(nullable = false, unique = true, length = 1024, updatable = false)
    private String objectKey;

    @Column(nullable = false, length = 255, updatable = false)
    private String originalName;

    @Column(nullable = false, length = 255, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long size;

    @Column(updatable = false)
    private Integer width;

    @Column(updatable = false)
    private Integer height;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected MessageAttachment() {
    }

    public static MessageAttachment forDirectChannel(DirectChannel directChannel, User author, String objectKey,
                                                      String originalName, String contentType, long size,
                                                      Integer width, Integer height, Instant expiresAt) {
        MessageAttachment attachment = create(author, objectKey, originalName, contentType, size, width, height,
                expiresAt);
        attachment.directChannel = Objects.requireNonNull(directChannel, "directChannel is required");
        return attachment;
    }

    public static MessageAttachment forServerChannel(ServerChannel serverChannel, User author, String objectKey,
                                                      String originalName, String contentType, long size,
                                                      Integer width, Integer height, Instant expiresAt) {
        MessageAttachment attachment = create(author, objectKey, originalName, contentType, size, width, height,
                expiresAt);
        attachment.serverChannel = Objects.requireNonNull(serverChannel, "serverChannel is required");
        return attachment;
    }

    public void attachTo(DirectMessage message) {
        Objects.requireNonNull(message, "message is required");
        requirePending();
        if (directChannel == null || !sameEntity(directChannel.getId(), message.getDirectChannel().getId())) {
            throw new IllegalArgumentException("Direct message must belong to the attachment target channel");
        }
        requireSameAuthor(message.getAuthor());
        directMessage = message;
    }

    public void attachTo(ChannelMessage message) {
        Objects.requireNonNull(message, "message is required");
        requirePending();
        if (serverChannel == null || !sameEntity(serverChannel.getId(), message.getChannel().getId())) {
            throw new IllegalArgumentException("Channel message must belong to the attachment target channel");
        }
        requireSameAuthor(message.getAuthor());
        channelMessage = message;
    }

    public boolean isPending() {
        return directMessage == null && channelMessage == null;
    }

    private static MessageAttachment create(User author, String objectKey, String originalName, String contentType,
                                            long size, Integer width, Integer height, Instant expiresAt) {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        if (width != null && width < 0) {
            throw new IllegalArgumentException("width must not be negative");
        }
        if (height != null && height < 0) {
            throw new IllegalArgumentException("height must not be negative");
        }

        MessageAttachment attachment = new MessageAttachment();
        attachment.id = UUID.randomUUID().toString();
        attachment.author = Objects.requireNonNull(author, "author is required");
        attachment.objectKey = requireText(objectKey, "objectKey");
        attachment.originalName = requireText(originalName, "originalName");
        attachment.contentType = requireText(contentType, "contentType");
        attachment.size = size;
        attachment.width = width;
        attachment.height = height;
        attachment.createdAt = Instant.now();
        attachment.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        return attachment;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Attachment is already attached to a message");
        }
    }

    private void requireSameAuthor(User messageAuthor) {
        if (messageAuthor == null || !sameEntity(author.getId(), messageAuthor.getId())) {
            throw new IllegalArgumentException("Message author must match attachment author");
        }
    }

    private static boolean sameEntity(Object leftId, Object rightId) {
        return leftId != null && leftId.equals(rightId);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
